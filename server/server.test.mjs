import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createServer} from './server.mjs';
test('catalog, search, download, missing format and invalid paths',async()=>{
 const s=createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));
 const base='http://127.0.0.1:'+s.address().port;
 try{
  const list=await (await fetch(base+'/api/v1/books?q=shelley')).json();assert.equal(list.books.length,1);
  assert.equal((await (await fetch(base+'/api/v1/books?q=missing')).json()).books.length,0);
  const r=await fetch(base+list.books[0].formats[0].downloadUrl);assert.equal(r.status,200);assert.equal(r.headers.get('content-type'),'application/epub+zip');assert.equal(Buffer.from(await r.arrayBuffer()).subarray(0,2).toString(),'PK');
  assert.equal((await fetch(base+'/api/v1/books/ozymandias/download/pdf')).status,404);
  assert.equal((await fetch(base+'/catalog.json')).status,404);
  assert.equal((await fetch(base+'/')).status,200);
 }finally{await new Promise(r=>s.close(r));}
});
