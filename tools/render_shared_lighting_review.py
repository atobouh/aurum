#!/usr/bin/env python3
"""Generate the offline alpha review from original captures and development telemetry."""
from pathlib import Path
import argparse,sys,json,base64,hashlib,html,re,math,zipfile
root=Path(__file__).resolve().parents[1]
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
import markdown2
parser=argparse.ArgumentParser();parser.add_argument('--evidence',type=Path,default=Path('/workspace/scratch/aurum-shared-lighting'));args=parser.parse_args();work=args.evidence
esc=html.escape
labels={
'static-normal':('Static sources · shared lighting','Torchlit receiver board. The camera is close enough for the models to receive the local field.'),
'static-paths':('One surface evaluator','Cyan marks the shared terrain/model path. Particles and unsupported shaders retain their own rendering.'),
'static-coverage':('Actual field coverage','Green is field authority, red is propagated fallback. Green can represent valid darkness; it does not mean the surface is illuminated.'),
'static-block':('Local illumination before albedo','The shared local-light contribution, including the current local AO treatment. Aurum post effects are bypassed in this diagnostic.'),
'static-normals':('World-space normals','Terrain and model normal adapters, encoded as colour. This is geometric orientation, not a beauty image.'),
'static-sky':('Sealed-room sky control','Skylight contribution before albedo. Native emissive exceptions and unsupported particles are not skylight measurements.'),
'static-direct':('Sealed-room direct-light control','Sun/moon contribution before albedo. This is a negative control, not a positive god-ray test.'),
'static-mixed-sources':('Warm and soul light','Multiple static source colours, evaluated at each receiver’s actual position.'),
'static-midnight':('Same room · midnight','The source setup is retained while time and exposure change.'),
'static-sunset':('Same room · sunset','Checks the local-light path through the daylight transition; this sealed room does not validate outdoor atmosphere.'),
'static-room-unlit':('Room lights removed','The intentional room emitters are removed; the snapshot records a complete zero-source field.'),
'static-light-restored':('Room light restored','A new revision after the torch is restored.'),
'static-fullbright':('Explicit full-bright model','A display entity with brightness override retains its native appearance in the otherwise unlit room.'),
'static-fullbright-path':('Full-bright exception marked','Orange identifies explicit full-bright model draws; cyan identifies the shared surface evaluator.'),
'static-nightvision':('Night-vision preservation','Native lighting bypass is used while night vision is active; this is not a voxel-lighting frame.'),
'static-crisp':('Crisp visibility option','World-grid receiver sampling and thresholded local visibility. This is not full-resolution or alias-free ray tracing.'),
'static-detail8':('Half-block visibility probes','Higher probe density in the same room. CPU/cache cost still requires target-device measurement.'),
'compat-inventory':('Inventory/UI preservation','Creative inventory drawn with Aurum active. This is a manual UI regression frame; its world snapshot was taken immediately before opening the screen.'),
'compat-normal':('LambDynamicLights · compatibility mode','The static voxel replacement is paused. Terrain and supported models use their propagated light coordinates through the shared evaluator.'),
'compat-paths':('Compatibility mode · common evaluator','Cyan verifies the supported surface path; it does not imply voxel lighting is active.'),
'compat-unlit':('Compatibility control · no room lamp','The intentional room lamps and held light are removed before the dynamic-light control.'),
'compat-held':('Compatibility · held torch','The held source is supplied by LambDynamicLights. This build does not voxel-occlude moving sources.'),
'compat-moving-source':('Compatibility · moving source and receiver','A held-light test source and receiver use the development motion controller. See the associated clip and telemetry.'),
'outdoor-noon':('Generated scenery · noon','The documented mod/datapack subset is loaded. This is the disposable test save, not the user’s actual Regasm save.'),
'outdoor-sunset':('Generated scenery · sunset','Outdoor regression control with the shared model/terrain paths.'),
'outdoor-panorama':('Modded forest · wider view','A wider view of the generated scenery with the documented subset loaded. It is not the user’s original save.'),
'outdoor-night':('Generated scenery · night','Night regression control; no reference-parity or Intel UHD performance claim.'),
'water-night':('Natural water · night','Same shoreline camera with time changed. No held light is used in this frame.'),
'water-day':('Natural water · daylight','Water’s local light curve shares source accounting; water style and reflections are not redesigned.'),
'water-night-held':('Natural water · held light at night','Dynamic compatibility uses propagated light coordinates, including water’s local scattering term.'),
'natural-cave-unlit':('Natural cave · unresolved control','Generated dripstone scene. Distant sky/void gaps are visible: cavern acceptance remains open, and the cause of those gaps is not established.'),
'natural-cave-held':('Natural cave · held light, acceptance open','The held source lights nearby dripstone in compatibility mode. Distant sky/void gaps remain visible. This frame is evidence of an unresolved scene, not a cavern beauty pass.')}
def mode(s):
 v=s['voxel']
 if s['terrainMode']==0:return 'Native lighting bypass'
 if v['dynamicCompatibilityFallback']:return 'Dynamic-light compatibility'
 if v.get('sourceOverflowFallback'):return 'Source-budget fallback'
 if v['usable']:return 'Known-zero local field' if not v['hasSources'] else 'Static voxel visibility'
 return 'Unready / fallback'
manifest=[];figures=[];rows=[]
order=list(labels)
def capture_order(p):
 name=p.stem
 group=0 if name.startswith('static-') else 1 if name.startswith('compat-') else 2
 return group, order.index(name) if name in order else len(order), name
for p in sorted((work/'captures').glob('*.png'),key=capture_order):
 name=p.stem
 if not name.startswith(('static-','compat-','outdoor-','water-','natural-cave-')):continue
 m=json.loads(p.with_suffix('.json').read_text());s=m['snapshot'];v=s['voxel']
 assert s['aurum']=='0.6.2-alpha.1',name
 assert v.get('snapshotSide')==56,name
 data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==m['sha256'],name
 title,caption=labels.get(name,(name.replace('-',' ').capitalize(),'Original game capture; exact state is recorded in the evidence JSON.'))
 if name.startswith('static-exact-'):
  count=int(name.rsplit('-',1)[1]);title=f'Controlled source count · {count}';caption='Elevated sealed fixture excludes native cave emitters. Counts 1–32 use the voxel field; 33 verifies the explicit budget fallback; zero verifies a complete dark field.'
 elif name=='static-source-overflow':title='Dense room-light control';caption='Use the recorded candidate count and mode: this earlier fixture name is not a claim that the 32-source budget is exceeded.'
 elif name=='static-after-overflow':title='Dense sources removed';caption='A settled revision after removing the temporary source grid.'
 manifest.append(dict(m,title=title,caption=caption))
 figures.append('<figure><button class="image" aria-label="Enlarge '+esc(title,quote=True)+'"><img loading="lazy" src="data:image/png;base64,'+base64.b64encode(data).decode()+'" alt="'+esc(title,quote=True)+'"></button><figcaption><strong>'+esc(title)+'</strong><p>'+esc(caption)+'</p><small>'+esc(mode(s))+' · candidates '+str(v['publishedCandidates'])+' · detail '+str(v['detail'])+'</small></figcaption></figure>')
 rows.append('<tr><td>'+esc(name)+'</td><td>'+esc(mode(s))+'</td><td>'+str(s['worldTime'])+'</td><td>'+str(v['publishedCandidates'])+'</td><td>'+str(v['publishedRevision'])+' / '+str(v['revision'])+'</td></tr>')
clips=[];motion=[]
for name in ['static-receiver-and-camera','compat-receiver-source-camera','day-night-transition']:
 p=work/(name+'.webm');j=p.with_suffix('.json')
 if not p.exists() or not j.exists():continue
 d=json.loads(j.read_text());frames=d['frames'];assert frames,name
 cameras=[list(f['camera']) for f in frames]
 for i in range(1,len(cameras)):
  cameras[i][3]=cameras[i-1][3]+(cameras[i][3]-cameras[i-1][3]+180)%360-180
 stats={'name':name,'frames':len(frames),'durationMillis':frames[-1]['elapsedMillis']-frames[0]['elapsedMillis'],'cameraRange':[max(v[i] for v in cameras)-min(v[i] for v in cameras) for i in range(5)],'entityTypes':sorted({e['type'] for f in frames for e in f.get('observedEntities',[])}),'videoSha256':hashlib.sha256(p.read_bytes()).hexdigest()}
 motion.append(dict(stats,telemetry=d));clips.append('<figure><video controls preload="metadata" src="data:video/webm;base64,'+base64.b64encode(p.read_bytes()).decode()+'"></video><figcaption><strong>'+esc(name.replace('-',' ').capitalize())+'</strong><p>Actual software-rendered footage. '+str(len(frames))+' recorded world frames; camera X/Y/Z/yaw/pitch spans '+esc(str([round(v,2) for v in stats['cameraRange']]))+'. Capture rate is not game FPS or an Intel UHD measurement.</p></figcaption></figure>')
jar=root/'dist/aurum-0.6.2-alpha.1.jar';release={'file':jar.name,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'bytes':jar.stat().st_size} if jar.exists() else None
results=json.loads((work/'runtime-results.json').read_text()) if (work/'runtime-results.json').exists() else {'status':'Runtime checks in progress; table records actual captures only.'}
evidence={'version':'0.6.2-alpha.1','release':release,'results':results,'captures':manifest,'motion':motion,'limits':['Not the full user modpack/save','No target-device frame-time or latency measurements','Dynamic compatibility pauses static voxel replacement','Not complete daylight GI, source-aware air or contribution-aware post AO']}
(root/'docs/research/lighting-062-alpha1-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
css='''*{box-sizing:border-box}html{color-scheme:dark;background:#0c1518;color:#e5ece9;font:17px/1.65 system-ui,sans-serif}body{margin:0;overflow-wrap:anywhere}main,header{max-width:1280px;margin:auto;padding:36px 28px}header{padding-top:64px}h1{font:clamp(36px,5vw,68px)/1.1 Georgia,serif;max-width:1000px}h2{font:31px/1.3 Georgia,serif;margin-top:44px}h3{font-size:22px;color:#f0ba7d}a{color:#9de5e2}code{font-size:.87em;background:#1a2a30;padding:2px 5px}.eyebrow{font:700 12px/1.5 system-ui;letter-spacing:.15em;color:#f0ba7d;text-transform:uppercase}.lead{font-size:21px;max-width:920px}.notice{border-left:4px solid #eeb87c;background:#18272c;padding:20px 24px}.gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}figure{margin:0;background:#14242a;border:1px solid #355059;min-width:0}figure img,video{width:100%;display:block}.image{display:block;width:100%;border:0;padding:0;background:transparent;cursor:zoom-in}figcaption{padding:18px}figcaption p{font-size:14px;color:#bbcacc;margin:8px 0}small{font-size:12px;color:#eeb87c}.table-scroll{overflow-x:auto;margin:24px 0}table{border-collapse:collapse;width:100%;font-size:14px;min-width:640px}th,td{padding:12px;text-align:left;vertical-align:top;border-bottom:1px solid #355059}th{background:#1c3037;color:#f0ba7d}details{margin:28px 0;padding:20px;background:#14242a}summary{cursor:pointer;color:#9de5e2}pre{white-space:pre-wrap;font-size:12px}dialog{padding:12px;border:1px solid #6a929b;max-width:96vw;max-height:96vh;background:#0c1518}dialog::backdrop{background:#000c}dialog img{max-width:90vw;max-height:80vh}dialog button{display:block;padding:8px 20px;margin:0 0 10px auto;cursor:pointer}@media(max-width:700px){main,header{padding:24px 16px}.gallery{grid-template-columns:1fr}.lead{font-size:18px}}'''
md=markdown2.markdown((root/'docs/research/LIGHTING_IMPLEMENTATION_062_ALPHA1.md').read_text(),extras=['tables','fenced-code-blocks'])
body='<header><p class="eyebrow">Aurum · 0.6.2-alpha.1 · lighting integration</p><h1>One surface-lighting path.</h1><p class="lead">Terrain and supported models share source evaluation, colour and display mapping. Real game captures and motion telemetry show what is implemented and what remains open.</p><p class="notice"><strong>Experimental build.</strong> This is the first integrated lighting milestone, not completion of the whole plan. With LambDynamicLights installed, the static voxel replacement is paused in favour of a consistent propagated-light path.</p>'
if release:body+='<p><a href="https://raw.githubusercontent.com/atobouh/aurum/codex/dist/'+jar.name+'">Download the alpha JAR</a> · <a href="https://raw.githubusercontent.com/atobouh/aurum/codex/docs/research/lighting-062-alpha1-evidence.json">Download evidence JSON</a></p>'
if release:body+='<p><small>JAR · '+str(release['bytes'])+' bytes · SHA-256 '+release['sha256']+'</small></p>'
body+='</header><main><h2>Runtime results</h2><div class="table-scroll"><table><thead><tr><th>Check</th><th>Result and scope</th></tr></thead><tbody>'+''.join('<tr><td>'+esc(str(k).replace('_',' ').capitalize())+'</td><td>'+esc(str(v))+'</td></tr>' for k,v in results.items())+'</tbody></table></div><h2>Actual motion footage</h2><p>Motion is recorded separately from still-image acceptance. These clips cannot establish 60-FPS behaviour on Intel UHD. Moving receivers are tested; moving-entity shadows from local lamps are not implemented.</p><div class="gallery">'+''.join(clips)+'</div><h2>Original game captures</h2><p>The constructed room and elevated source-count fixture are in a disposable copy of the documented modded save. Captures wait for a settled matching cache, except explicitly native/compatibility bypasses. Natural scenes are labelled separately.</p><div class="gallery">'+''.join(figures)+'</div><h2>Capture state</h2><div class="table-scroll"><table><thead><tr><th>Frame</th><th>Actual mode</th><th>World time</th><th>Candidate sources</th><th>Published / current revision</th></tr></thead><tbody>'+''.join(rows)+'</tbody></table></div><details><summary>Implementation, verification and remaining gates</summary>'+md+'</details></main><dialog><button>Close</button><img alt="Enlarged original capture"></dialog><script>const d=document.querySelector("dialog");document.querySelectorAll(".image").forEach(b=>b.onclick=()=>{d.querySelector("img").src=b.querySelector("img").src;d.showModal()});d.querySelector("button").onclick=()=>d.close();</script>'
p=root/'docs/reports/aurum-0.6.2-alpha.1-lighting-review.html';p.write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Aurum — shared lighting alpha review</title><style>'+css+'</style>'+body+'</html>')
print(p,len(manifest),'images',len(motion),'clips',p.stat().st_size,'bytes')

# Raw GitHub HTML is served as text/plain. Offer a real offline download whose
# index uses ordinary relative media files instead of enormous data-URI lines.
bundle=root/'dist/aurum-0.6.2-alpha.1-lighting-report.zip'
prefix='Aurum-lighting-report/'
with zipfile.ZipFile(bundle,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as archive:
 images=iter(manifest);videos=iter(motion);packaged=[]
 def extract_image(match):
  item=next(images);name=item['image'];assert Path(name).name==name
  data=base64.b64decode(match.group(1),validate=True)
  assert hashlib.sha256(data).hexdigest()==item['sha256'],name
  archive.writestr(prefix+'media/'+name,data);packaged.append(name)
  return 'src="media/'+name+'"'
 def extract_video(match):
  item=next(videos);name=item['name']+'.webm';assert Path(name).name==name
  data=base64.b64decode(match.group(1),validate=True)
  assert hashlib.sha256(data).hexdigest()==item['videoSha256'],name
  archive.writestr(prefix+'media/'+name,data);packaged.append(name)
  return 'src="media/'+name+'"'
 offline=re.sub(r'src="data:image/png;base64,([^"]+)"',extract_image,p.read_text())
 offline=re.sub(r'src="data:video/webm;base64,([^"]+)"',extract_video,offline)
 assert len(packaged)==len(manifest)+len(motion)
 assert 'data:image/png;base64,' not in offline and 'data:video/webm;base64,' not in offline
 offline=offline.replace('https://raw.githubusercontent.com/atobouh/aurum/codex/docs/research/lighting-062-alpha1-evidence.json','evidence.json')
 # Lead with the actual screenshots rather than a long table of test results.
 start=offline.index('<h2>Original game captures</h2>');end=offline.index('<h2>Capture state</h2>',start)
 gallery=offline[start:end];offline=offline[:start]+offline[end:]
 offline=offline.replace('<main>','<main>'+gallery,1)
 offline=offline.replace('<p class="lead">',f'<p>Offline report: all {len(manifest)} screenshots and {len(motion)} videos are included in the adjacent media folder. Keep the extracted folder together.</p><p class="lead">',1)
 offline=offline.replace('</figure>','</figure>\n')
 archive.writestr(prefix+'index.html',offline)
 archive.writestr(prefix+'evidence.json',json.dumps(evidence,indent=2)+'\n')
 archive.writestr(prefix+'README.txt',
  'Aurum 0.6.2-alpha.1 lighting report\n\n'
  '1. Extract the entire ZIP (Windows: right-click, Extract All).\n'
  '2. Open Aurum-lighting-report/index.html in Chrome, Edge or Firefox.\n'
  '3. Keep index.html, evidence.json and the media folder together.\n\n'
  f'All {len(manifest)} original PNG screenshots and {len(motion)} WebM clips are included.\n'
  'They load offline, without GitHub, a local server or installation.\n'
  'The alpha JAR download is a separate online link inside the report.\n\n'
  'This is an experimental lighting milestone. Cave acceptance, dynamic\n'
  'voxel source transport and target Intel UHD measurements remain open.\n')
print(bundle,bundle.stat().st_size,'bytes; offline HTML with',len(manifest),'original images and',len(motion),'clips')
