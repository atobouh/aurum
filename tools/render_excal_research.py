#!/usr/bin/env python3
"""Build the illustrated research report and an offline ZIP; never bundles the resource pack."""
from pathlib import Path
import argparse,base64,hashlib,html,io,json,re,sys,zipfile
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
import markdown2
parser=argparse.ArgumentParser();parser.add_argument('--evidence',type=Path,required=True);a=parser.parse_args();w=a.evidence;root=Path(__file__).resolve().parents[1];esc=html.escape
project=json.loads((w/'modrinth-project.txt').read_text());audit=json.loads((w/'asset-height-audit.json').read_text());media={};captured=[];references=[]
def card(name,data,title,caption,attribution=''):
 assert name==Path(name).name
 media['media/'+name]=data
 return '<figure><button class="zoom" aria-label="Enlarge '+esc(title,quote=True)+'"><img loading="lazy" src="media/'+esc(name,quote=True)+'" alt="'+esc(title,quote=True)+'"></button><figcaption><strong>'+esc(title)+'</strong><p>'+esc(caption)+'</p>'+attribution+'</figcaption></figure>'
refnotes={1:('Windmill · form and pixel art','Beams, stairs and trim create real form; directional light and dark pixel groups clarify it. This image does not identify its shader or prove POM use.'),2:('Gate · recessed and layered details','Readable frame depth, lantern geometry and material borders. A normal-map effect alone cannot generate all the visible geometry.'),4:('Sandstone · block shape remains readable','Layered construction and distinct block borders carry much of the impression. Avoid turning every colour pixel into a spike.'),5:('Night · light separates forms','Selective local light emphasizes materials and silhouette. This is a source image, not a matched Aurum comparison.')}
refcards=[]
for i,(title,note) in refnotes.items():
 p=w/f'gallery-{i}.jpeg';data=p.read_bytes();g=project['gallery'][i];url=g['raw_url'];references.append({'file':p.name,'url':url,'sha256':hashlib.sha256(data).hexdigest(),'attribution':'Excalibur / Maffhew, official Modrinth gallery','scope':'Reference image; pack revision and rendering setup not disclosed.'})
 refcards.append(card(p.name,data,title,note,'<small>Reference: Excalibur / Maffhew · <a href="'+esc(url,quote=True)+'">official gallery image</a></small>'))
case_notes={
'flat':('Flat control','Generated normals off · parallax off · sharpening 0. Existing pack art and authored models remain.'),
'normals':('Generated normals only','Normals on at 50 · parallax off · sharpening 0. Same overview camera and frozen world time.'),
'relief':('Existing generated relief','Normals 50 · parallax on, depth 100, distance 16 · sharpening 0. LabPBR remains off.'),
'relief-sharp65':('Same relief · sharpening 65','Matches the preceding material settings and camera; sharpening changes local contrast, not geometry.'),
'close':('Existing relief · close-up','A different, recorded camera to inspect the surface. Not a same-camera comparison with the overview.'),
'native-models':('Aurum disabled · art/geometry control','Excalibur still supplies authored textures and models. Lighting is native, so this is not a matched lighting-quality comparison.')}
casecards={};camera={}
for pack in ['vanilla','excal']:
 cards=[]
 for case,(title,note) in case_notes.items():
  p=w/'captures'/f'{pack}-{case}.png'
  if not p.exists():continue
  d=json.loads(p.with_suffix('.json').read_text());data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==d['sha256'];s=d['snapshot'];assert not s['config']['labPbr'] and s['worldTime']==3000
  if case!='close':
   if pack in camera:assert s['camera']==camera[pack],case
   else:camera[pack]=s['camera']
  captured.append(d);cards.append(card(p.name,data,('Vanilla · ' if pack=='vanilla' else 'Excalibur · ')+title,note))
 casecards[pack]=''.join(cards)
assert len(captured)==11
# Preserve per-capture state, pinned inventory, original image hashes and access limitations.
evidence={'scope':'Research only; existing renderer, not a new visual implementation','rendererCommit':'b8f5a5b17d64e37d45162109ae1d818dd45ce848','trailer':{'url':'https://youtu.be/7L6GUlEeUNA','reviewed':False,'reason':'YouTube access returned proxy HTTP 403; no trailer frames or settings verified.'},'references':references,'audit':audit,'baselineChecks':json.loads((w/'baseline-checks.json').read_text()),'captures':captured,'limits':['No new renderer implementation','No motion acceptance from still images','Not an Intel UHD benchmark','Generated test fixture and documented mod subset, not the original user save','Native background clouds were not frozen; material controls use fixed camera/time and no Aurum cloud shadows']}
(root/'docs/research/excalibur-relief-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
bodymd=(root/'docs/research/EXCALIBUR_RELIEF_RESEARCH.md').read_text().split('\n',1)[1];analysis=markdown2.markdown(bodymd,extras=['tables','fenced-code-blocks'])
css='''*{box-sizing:border-box}html{background:#0c1518;color:#e5ece9;color-scheme:dark;font:17px/1.65 system-ui,sans-serif}body{margin:0;overflow-wrap:anywhere}header,main{max-width:1300px;margin:auto;padding:32px 28px}header{padding-top:58px}h1{font:clamp(38px,5vw,70px)/1.1 Georgia,serif;max-width:960px}h2{font:30px/1.25 Georgia,serif;margin:42px 0 18px}h3{font-size:21px;color:#f0ba7d}.eyebrow{color:#f0ba7d;font-size:12px;font-weight:700;letter-spacing:.15em;text-transform:uppercase}.lead{font-size:21px;max-width:1000px}a{color:#9de5e2}.notice{padding:18px 22px;background:#203037;border-left:4px solid #eeb87c}.gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:20px}figure{margin:0;border:1px solid #355059;background:#14242a;min-width:0}figure img{display:block;width:100%}.zoom{padding:0;border:0;background:transparent;width:100%;display:block;cursor:zoom-in}figcaption{padding:18px}figcaption p{font-size:14px;color:#bdcdd0;margin:8px 0}small{font-size:12px;color:#eeb87c}table{width:100%;border-collapse:collapse;font-size:14px;display:block;overflow:auto}td,th{padding:12px;text-align:left;border-bottom:1px solid #355059}th{background:#203037}code{background:#203037;padding:2px 5px;font-size:.85em}.data{font-variant-numeric:tabular-nums}dialog{padding:12px;border:1px solid #75969b;max-width:96vw;max-height:96vh;background:#0c1518}dialog img{max-width:90vw;max-height:80vh}dialog::backdrop{background:#000c}dialog button{display:block;margin:0 0 10px auto;padding:8px 18px;cursor:pointer}nav{display:flex;flex-wrap:wrap;gap:18px}@media(max-width:700px){header,main{padding:24px 16px}.gallery{grid-template-columns:1fr}.lead{font-size:18px}}'''
header='''<header><p class="eyebrow">Aurum · Excalibur reference research · LabPBR off</p><h1>Sharp surface depth from ordinary textures.</h1><p class="lead">What the reference actually provides, what Aurum already does, and which changes could make depth more convincing across resource packs.</p><p class="notice"><strong>Research, not a new visual build.</strong> The gallery and downloaded pack were inspected. YouTube playback was blocked here; the linked trailer has not been watched. The Minecraft images below use the existing renderer with LabPBR disabled.</p><nav><a href="#references">Reference images</a><a href="#baseline">Actual Aurum controls</a><a href="#research">Findings and engineering plan</a><a href="evidence.json">Evidence JSON</a></nav></header>'''
rows=[]
for n,v in audit['heightMeasurements'].items():
 rows.append('<tr><td>'+esc(n)+'</td><td>'+f'{v["inputLuminanceRange"]:.4f}'+'</td><td>'+str(v['minHeightByte'])+'–'+str(v['maxHeightByte'])+'</td><td>'+f'{v["rangeBlocksAtDepth100"]:.5f}'+'</td></tr>')
main='<main><h2 id="references">Official gallery · observed references</h2><p>Excalibur by Maffhew. These are attributed source images, not Aurum renders. Their shader/PBR add-ons and pack revision are unverified.</p><div class="gallery">'+''.join(refcards)+'</div><h2 id="baseline">Actual Minecraft · vanilla textures</h2><p>Same board and overview camera, world time frozen at 3000. The separate close-up camera is labelled. Generated normals, parallax and sharpening are isolated; no new algorithm has been implemented.</p><div class="gallery">'+casecards['vanilla']+'</div><h2>Actual Minecraft · Excalibur, LabPBR off</h2><p>The pinned resource pack is loaded. Runtime logs record zero authored PBR sprites. Models and art come from Excalibur. The native-lighting frame is an art/geometry control, not a matched lighting comparison. Native background clouds move between captures; the material camera and time are fixed.</p><div class="gallery">'+casecards['excal']+'</div><p class="notice"><strong>Coverage issue found:</strong> enabling POM after normals barely changes the Excalibur overview material ROI. Its depth-only path is not validated here. The runtime gate needs investigation before any claim of pack-wide parallax. The 48×48 oak atlas is one known unsupported layout.</p><h2>Measured current height generator</h2><p>The unchanged production Java method is executed directly. Equal output depth across differing input contrasts exposes the normalization policy. Flat painted synthetic motifs have no physical relief: the generated depth there is false by construction.</p><table class="data"><thead><tr><th>Input</th><th>Luminance range</th><th>Height bytes</th><th>Depth range, blocks</th></tr></thead><tbody>'+''.join(rows)+'</tbody></table><section id="research">'+analysis+'</section></main>'
footer='''<dialog><button>Close</button><img alt="Enlarged original image"></dialog><script>const d=document.querySelector('dialog');document.querySelectorAll('.zoom').forEach(b=>b.onclick=()=>{d.querySelector('img').src=b.querySelector('img').src;d.showModal()});d.querySelector('button').onclick=()=>d.close();</script>'''
offline='<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aurum — Excalibur surface-depth research</title><style>'+css+'</style>'+header+main+footer+'</html>'
# Also keep a complete single-file HTML source, while the ZIP is the primary download.
standalone=offline
for name,data in media.items():
 mime='image/png' if name.endswith('.png') else 'image/jpeg';standalone=standalone.replace('src="'+name+'"','src="data:'+mime+';base64,'+base64.b64encode(data).decode()+'"')
standalone=standalone.replace('href="evidence.json"','href="https://raw.githubusercontent.com/atobouh/aurum/codex/docs/research/excalibur-relief-evidence.json"')
(root/'docs/reports/aurum-excalibur-relief-research.html').write_text(standalone)
bundle=root/'dist/aurum-excalibur-relief-research.zip';prefix='Aurum-Excalibur-research/'
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 z.writestr(prefix+'index.html',offline);z.writestr(prefix+'evidence.json',json.dumps(evidence,indent=2)+'\n');z.writestr(prefix+'research.md',(root/'docs/research/EXCALIBUR_RELIEF_RESEARCH.md').read_bytes())
 for n,b in media.items():z.writestr(prefix+n,b)
 z.writestr(prefix+'README.txt','Extract the entire ZIP. Open Aurum-Excalibur-research/index.html in Chrome, Edge or Firefox. Keep the media folder beside it. All 15 images are included.\n\nResearch only. No new mod JAR. YouTube trailer playback remains pending; the report says which reference material was actually reviewed.\n\nReference gallery: Excalibur / Maffhew, attributed Modrinth images. The resource pack itself is not included.\n')
print('Packaged',len(references),'reference images +',len(captured),'actual Minecraft captures;',bundle.stat().st_size,'bytes')
