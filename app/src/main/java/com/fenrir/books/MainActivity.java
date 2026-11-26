package com.fenrir.books;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.URLUtil;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BG=0xff101013, PANEL=0xff1c191e, GOLD=0xffd7b66b, WHITE=0xfff4efe5, MUTED=0xffb4aaa0;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private LinearLayout body, results, queue;
    private EditText search;
    private TextView status;
    private SharedPreferences prefs;
    private DownloadManager dm;
    private String next;
    private int generation=0;
    private boolean queueVisible=false;
    private final Runnable tick=new Runnable(){public void run(){if(queueVisible)showDownloads();ui.postDelayed(this,2500);}};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("fenrir",MODE_PRIVATE);dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        body=column();body.setPadding(dp(20),dp(24),dp(20),dp(24));scroll.addView(body);setContentView(scroll);
        body.setOnApplyWindowInsetsListener((v,insets)->{if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20)+bars.left,dp(20)+bars.top,dp(20)+bars.right,dp(20)+bars.bottom);}return insets;});
        header("FENRIR BOOKS",23,GOLD);header("Your next story awaits.",31,WHITE);header("Discover and download books for offline reading.",14,MUTED);
        LinearLayout nav=row();nav.addView(button("Discover",v->showCatalog()),weight());nav.addView(button("Downloads",v->{queueVisible=true;results.setVisibility(View.GONE);queue.setVisibility(View.VISIBLE);showDownloads();}),weight());body.addView(nav);
        LinearLayout options=row();options.addView(button("Book source",v->sourceDialog()),weight());options.addView(button("Add HTTPS link",v->directDialog()),weight());body.addView(options);
        body.addView(button("Browse websites",v->browserDialog()),full());
        body.addView(button("Discord Files · Open in browser",v->openDiscordFiles()),full());
        search=new EditText(this);search.setSingleLine(true);search.setHint("Search title or author");search.setHintTextColor(MUTED);search.setTextColor(WHITE);search.setBackground(box(PANEL,12));search.setPadding(dp(14),dp(13),dp(14),dp(13));body.addView(search,full());search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setOnEditorActionListener((v,a,e)->{load(false);return true;});
        body.addView(button("Search catalog",v->load(false)),full());
        status=label("",14,MUTED);body.addView(status,full());results=column();body.addView(results,full());queue=column();queue.setVisibility(View.GONE);body.addView(queue,full());
        showCatalog();load(false);ui.postDelayed(tick,2500);
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams full(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),dp(8));return p;}
    private GradientDrawable box(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String t,int size,int color){TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(7));return v;}
    private void header(String t,int size,int color){TextView v=label(t,size,color);if(size>20)v.setTypeface(Typeface.SERIF,Typeface.BOLD);body.addView(v,full());}
    private Button button(String t,View.OnClickListener l){Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setTextColor(BG);b.setBackground(box(GOLD,10));b.setOnClickListener(l);b.setPadding(dp(8),dp(12),dp(8),dp(12));return b;}
    private void showCatalog(){queueVisible=false;queue.setVisibility(View.GONE);results.setVisibility(View.VISIBLE);}
    private String base(){return prefs.getString("source","");}
    private void sourceDialog(){EditText input=new EditText(this);input.setHint("https://your-server.example");input.setText(base());new AlertDialog.Builder(this).setTitle("Book source").setMessage("Leave empty for Project Gutenberg via Gutendex. Or enter the HTTPS URL of your authorized Fenrir Books API.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{String s=input.getText().toString().trim().replaceAll("/+$","");if(!s.isEmpty()&&!https(s)){toast("Use a valid HTTPS address");return;}prefs.edit().putString("source",s).apply();load(false);}).show();}
    private boolean https(String s){try{URI u=new URI(s);return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null;}catch(Exception e){return false;}}
    private static final String[] FORMATS={"EPUB","PDF","MOBI","AZW3","CBZ","FB2","TXT"};
    private String mime(String kind){switch(kind){case "PDF":return "application/pdf";case "EPUB":return "application/epub+zip";case "MOBI":return "application/x-mobipocket-ebook";case "AZW3":return "application/vnd.amazon.ebook";case "CBZ":return "application/vnd.comicbook+zip";case "FB2":return "application/x-fictionbook+xml";case "TXT":return "text/plain";default:return "application/octet-stream";}}
    private String fileFormat(String filename,String contentType){
        String lower=filename.toLowerCase(Locale.ROOT);
        for(String f:FORMATS)if(lower.endsWith("."+f.toLowerCase(Locale.ROOT)))return f;
        if(contentType!=null)for(String f:FORMATS)if(mime(f).equalsIgnoreCase(contentType.split(";")[0].trim()))return f;
        return null;
    }
    private void confirmDownload(String address,String filename,String kind){
        if(!https(address)||kind==null)return;
        new AlertDialog.Builder(this).setTitle("Download "+kind+"?").setMessage(filename+"\nFrom: "+Uri.parse(address).getHost()+"\nOnly download books you have permission to save.").setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->enqueue(filename,kind,address)).show();
    }
    private void openDiscordFiles(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://server.elscione.com/Discord%20Files/")));}
        catch(android.content.ActivityNotFoundException e){toast("No browser installed");}
    }
    private void browserDialog(){
        android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Material_NoActionBar);
        LinearLayout layout=column();layout.setBackgroundColor(BG);layout.setPadding(dp(10),dp(14),dp(10),dp(10));
        EditText address=new EditText(this);address.setSingleLine(true);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setHint("https://website.example");address.setText(prefs.getString("lastWebsite","https://server.elscione.com/"));
        WebView web=new WebView(this);web.setBackgroundColor(WHITE);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setSafeBrowsingEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        Runnable open=()->{String url=address.getText().toString().trim();if(!url.contains("://"))url="https://"+url;if(!https(url)){toast("Use an HTTPS website");return;}prefs.edit().putString("lastWebsite",url).apply();web.loadUrl(url);};
        LinearLayout nav=row();nav.addView(button("Back",v->{if(web.canGoBack())web.goBack();}),weight());nav.addView(button("Go",v->open.run()),weight());nav.addView(button("Close",v->dialog.dismiss()),weight());layout.addView(nav);layout.addView(address,full());
        LinearLayout shortcuts=row();shortcuts.addView(button("Elscione",v->{address.setText("https://server.elscione.com/");open.run();}),weight());shortcuts.addView(button("Open in browser",v->{String url=address.getText().toString();if(https(url))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("No browser installed");}}),weight());layout.addView(shortcuts);
        layout.addView(button("Discord Files · Open in browser",v->openDiscordFiles()),full());
        layout.addView(label("Tap a permitted file link to download. If a website requires verification or blocks this browser, use Open in browser.",13,MUTED),full());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String url=request.getUrl().toString();if(!https(url))return true;
                if(request.isForMainFrame()&&request.hasGesture()){
                    String path=request.getUrl().getPath();String kind=fileFormat(path==null?"":path,null);
                    if(kind!=null){confirmDownload(url,URLUtil.guessFileName(url,null,mime(kind)),kind);return true;}
                }
                return false;
            }
            @Override public void onPageFinished(WebView view,String url){if(https(url))address.setText(url);}
        });
        web.setDownloadListener((url,userAgent,disposition,type,length)->{String name=URLUtil.guessFileName(url,disposition,type);String kind=fileFormat(name,type);if(kind==null){toast("Unsupported file type");return;}confirmDownload(url,name,kind);});
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(layout);dialog.setOnDismissListener(d->{web.stopLoading();web.destroy();});dialog.show();dialog.getWindow().setLayout(-1,-1);
        open.run();
    }
    private JSONObject getJson(String address)throws Exception{
        if(!https(address))throw new IOException("Invalid HTTPS URL");
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","FenrirBooks/1.0");c.setInstanceFollowRedirects(false);
        try{if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());try(InputStream stream=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] buf=new byte[8192];int count,total=0;while((count=stream.read(buf))!=-1){total+=count;if(total>5000000)throw new IOException("Catalog too large");bytes.write(buf,0,count);}return new JSONObject(bytes.toString("UTF-8"));}}finally{c.disconnect();}
    }
    private void load(boolean more){
        showCatalog();final int token=++generation;String source=base(),query=search.getText().toString().trim();final boolean gutenberg=source.isEmpty();String url;
        try{url=more?next:(gutenberg?"https://gutendex.com/books/?copyright=false&search=":source+"/api/v1/books?q=")+URLEncoder.encode(query,"UTF-8");}catch(Exception e){return;}
        if(url==null)return;if(!more)results.removeAllViews();status.setText("Loading books…");
        worker.execute(()->{try{
            JSONObject json=getJson(url);JSONArray books=json.getJSONArray(gutenberg?"results":"books");String newNext=gutenberg&&!json.isNull("next")?json.optString("next",null):null;
            ArrayList<Book> found=new ArrayList<>();for(int i=0;i<books.length();i++){
                JSONObject b=books.getJSONObject(i);Book book=new Book();book.title=b.optString("title","Untitled");
                if(gutenberg){if(b.optBoolean("copyright",true))continue;JSONArray authors=b.optJSONArray("authors");book.author=authors!=null&&authors.length()>0?authors.getJSONObject(0).optString("name"):"Unknown author";JSONObject formats=b.optJSONObject("formats");if(formats!=null){String epub=formats.optString("application/epub+zip");if(https(epub))book.links.put("EPUB",epub);String pdf=formats.optString("application/pdf");if(https(pdf))book.links.put("PDF",pdf);}book.description="Public domain in the United States · Project Gutenberg";}
                else{book.author=b.optString("author","Unknown author");book.description=b.optString("description")+" · "+b.optString("license");if(b.optString("license").trim().isEmpty())continue;JSONArray formats=b.optJSONArray("formats");if(formats!=null)for(int j=0;j<formats.length();j++){JSONObject f=formats.getJSONObject(j);String kind=f.optString("format").toUpperCase(Locale.ROOT);String link=new URI(source+"/").resolve(f.optString("downloadUrl")).toString();if((kind.equals("EPUB")||kind.equals("PDF"))&&https(link))book.links.put(kind,link);}}
                if(!book.links.isEmpty())found.add(book);
            }
            ui.post(()->{if(token!=generation||isFinishing())return;if(more&&results.getChildCount()>0)results.removeViewAt(results.getChildCount()-1);for(Book b:found)addCard(b);next=newNext;if(next!=null)results.addView(button("Load more",v->load(true)),full());status.setText(found.isEmpty()?"No downloadable matches.":gutenberg?"Project Gutenberg · Public-domain catalog":"Your authorized catalog");});
        }catch(Exception e){ui.post(()->{if(token==generation)status.setText("Could not load catalog. Check connection or source, then search again.");});}});
    }
    private static class Book{String title,author,description;Map<String,String> links=new LinkedHashMap<>();}
    private void addCard(Book book){
        LinearLayout card=column();card.setBackground(box(PANEL,14));card.setPadding(dp(18),dp(14),dp(18),dp(14));
        TextView title=label(book.title,21,WHITE);title.setTypeface(Typeface.SERIF,Typeface.BOLD);card.addView(title);card.addView(label(book.author,14,GOLD));card.addView(label(book.description,13,MUTED));LinearLayout actions=row();
        for(Map.Entry<String,String> f:book.links.entrySet())actions.addView(button("Save "+f.getKey(),v->enqueue(book.title,f.getKey(),f.getValue())),weight());card.addView(actions);results.addView(card,full());
    }
    private void directDialog(){LinearLayout fields=column();EditText title=new EditText(this);title.setHint("Book title");EditText link=new EditText(this);link.setHint("Authorized HTTPS book link");link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);fields.addView(title);fields.addView(link);Spinner format=new Spinner(this);format.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,FORMATS));fields.addView(format);new AlertDialog.Builder(this).setTitle("Add direct download").setMessage("Use only files you are permitted to download.").setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->enqueue(title.getText().toString().trim(),format.getSelectedItem().toString(),link.getText().toString().trim())).show();}
    private JSONArray records(){try{return new JSONArray(prefs.getString("downloads","[]"));}catch(Exception e){return new JSONArray();}}
    private void enqueue(String title,String kind,String link){
        if(title.isEmpty()||!https(link)){toast("Enter a title and valid HTTPS link");return;}
        if(android.os.Build.VERSION.SDK_INT<=28&&checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},10);toast("Allow storage and try the download again");return;}
        try{JSONArray records=records();for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);if(link.equals(r.optString("url"))&&state(r.getLong("id"))!=DownloadManager.STATUS_FAILED&&state(r.getLong("id"))!=-1){toast("Already added to downloads");return;}}
            String name=title.replaceAll("[^\\p{L}\\p{N} _.-]","_");if(name.length()>80)name=name.substring(0,80);name=System.currentTimeMillis()+"-"+name+"."+kind.toLowerCase(Locale.ROOT);
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(link));request.setTitle(title).setDescription("Fenrir Books").setMimeType(mime(kind)).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name);
            long id=dm.enqueue(request);JSONObject item=new JSONObject();item.put("id",id);item.put("title",title);item.put("kind",kind);item.put("url",link);records.put(item);prefs.edit().putString("downloads",records.toString()).apply();toast("Download added");if(queueVisible)showDownloads();
        }catch(Exception e){toast("Unable to start download");}
    }
    private int state(long id){try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){return c!=null&&c.moveToFirst()?c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)):-1;}catch(Exception e){return -1;}}
    private void showDownloads(){
        if(!queueVisible)return;queue.removeAllViews();status.setText("Downloads · saved to your device");JSONArray a=records();if(a.length()==0)queue.addView(label("Your downloads will appear here.",16,MUTED));
        for(int i=a.length()-1;i>=0;i--){try{JSONObject r=a.getJSONObject(i);long id=r.getLong("id");int s=state(id);LinearLayout card=column();card.setBackground(box(PANEL,12));card.setPadding(dp(16),dp(12),dp(16),dp(12));card.addView(label(r.getString("title"),19,WHITE));String text=s==DownloadManager.STATUS_SUCCESSFUL?"Completed":s==DownloadManager.STATUS_FAILED?"Failed":s==DownloadManager.STATUS_PAUSED?"Paused":s==-1?"Unavailable":"Downloading / queued";card.addView(label(r.getString("kind")+" · "+text,14,GOLD));
            if(s==DownloadManager.STATUS_SUCCESSFUL)card.addView(button("Open book",v->{try{Uri uri=dm.getUriForDownloadedFile(id);if(uri==null){toast("File unavailable");return;}Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,mime(r.optString("kind")));intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(intent);}catch(Exception e){toast("Install a compatible book reader to open this file.");}}));
            else if(s==DownloadManager.STATUS_FAILED||s==-1)card.addView(button("Retry",v->enqueue(r.optString("title"),r.optString("kind"),r.optString("url"))));
            else{try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){if(c!=null&&c.moveToFirst()){long done=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));long total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setIndeterminate(total<=0);if(total>0)bar.setProgress((int)Math.min(100,done*100.0/total));card.addView(bar,full());}}}
            queue.addView(card,full());
        }catch(Exception ignored){}}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){ui.removeCallbacks(tick);worker.shutdownNow();super.onDestroy();}
}
