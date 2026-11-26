import http from 'node:http';
import {readFile,stat} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {resolve,basename} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('.',import.meta.url));
const types={epub:'application/epub+zip',pdf:'application/pdf'};
const json=(res,status,value)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(value));};
export function createServer(){return http.createServer(async(req,res)=>{
  try{
    if(req.method!=='GET'&&req.method!=='HEAD')return json(res,405,{error:'Method not allowed'});
    const u=new URL(req.url,'http://localhost');
    const catalog=JSON.parse(await readFile(resolve(root,'catalog.json'),'utf8'));
    const publicBook=b=>({id:b.id,title:b.title,author:b.author,description:b.description,license:b.license,formats:Object.keys(b.formats).filter(f=>types[f]).map(f=>({format:f,downloadUrl:'/api/v1/books/'+encodeURIComponent(b.id)+'/download/'+f}))});
    if(u.pathname==='/api/v1/books'){
      const q=(u.searchParams.get('q')||'').trim().toLowerCase();
      return json(res,200,{books:catalog.filter(b=>[b.title,b.author,b.description].join(' ').toLowerCase().includes(q)).map(publicBook)});
    }
    // Opt-in link collection from this server's own configured book files.
    // This endpoint never fetches or crawls another website.
    if(u.pathname==='/api/v1/catalog-links'){
      const links=[],q=(u.searchParams.get('q')||'').trim().toLowerCase();
      for(const b of catalog){
        if(!/^[a-z0-9-]+$/.test(b.id)||typeof b.license!=='string'||!b.license.trim()||!b.formats||![b.title,b.author,b.description].join(' ').toLowerCase().includes(q))continue;
        for(const [format,name] of Object.entries(b.formats)){
          if(!types[format]||typeof name!=='string'||name!==basename(name)||!name.endsWith('.'+format))continue;
          try{if(!(await stat(resolve(root,'books',name))).isFile())continue;}catch{continue;}
          links.push({title:b.title,format,license:b.license,downloadUrl:'/api/v1/books/'+b.id+'/download/'+format});
          if(links.length>=100)return json(res,200,{source:'fenrir-authorized-catalog-v1',links});
        }
      }
      return json(res,200,{source:'fenrir-authorized-catalog-v1',links});
    }
    const m=u.pathname.match(/^\/api\/v1\/books\/([a-z0-9-]+)(?:\/download\/(epub|pdf))?$/);
    if(m){
      const b=catalog.find(x=>x.id===m[1]);
      if(!b)return json(res,404,{error:'Book not found'});
      if(!m[2])return json(res,200,publicBook(b));
      const f=m[2],name=b.formats[f];
      if(typeof name!=='string'||name!==basename(name)||!name.endsWith('.'+f)||!b.license)return json(res,404,{error:'Format unavailable'});
      const path=resolve(root,'books',name);
      const info=await stat(path);
      if(!info.isFile())return json(res,404,{error:'Unavailable'});
      res.writeHead(200,{'Content-Type':types[f],'Content-Length':info.size,'Content-Disposition':'attachment; filename*=UTF-8\'\''+encodeURIComponent(name),'X-Content-Type-Options':'nosniff'});
      if(req.method==='HEAD')return res.end();
      const stream=createReadStream(path);stream.on('error',()=>res.destroy());res.on('close',()=>stream.destroy());return stream.pipe(res);
    }
    if(u.pathname==='/'){
      const body=await readFile(resolve(root,'public','index.html'));
      res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'none'"});
      return res.end(req.method==='HEAD'?undefined:body);
    }
    return json(res,404,{error:'Not found'});
  }catch(e){if(!res.headersSent)json(res,e.code==='ENOENT'?404:500,{error:e.code==='ENOENT'?'File unavailable':'Server error'});else res.destroy();}
});}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  createServer().listen(Number(process.env.PORT||3000),process.env.HOST||'127.0.0.1',()=>console.log('Fenrir Books is ready.'));
}
