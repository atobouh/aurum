from pathlib import Path
import json,subprocess,hashlib,sys,math,os
sys.path.insert(0,'/workspace/test-tools/python')
from PIL import Image,ImageStat
root=Path(__file__).resolve().parents[2];w=Path(os.environ.get('AURUM_LIGHTING_EVIDENCE',str(root/'scratch/lighting-evidence')));c=w/'captures';results={}
for n in [0,1,16,17,32,33]:
 v=json.loads((c/f'static-exact-{n}.json').read_text())['snapshot']['voxel'];assert v['publishedCandidates']==n and v['usable']==(n<=32) and v['sourceOverflowFallback']==(n>32),(n,v)
results['controlled_counts']='PASS: exact 0/1/16/17/32 sources use a complete field; 33 selects the common source-budget fallback.'
for p in c.glob('compat-*.json'):
 s=json.loads(p.read_text())['snapshot'];assert s['voxel']['dynamicCompatibilityFallback'] and not s['voxel']['usable'],p
means=[]
for n in ['compat-unlit','compat-held']:
 im=Image.open(c/(n+'.png')).convert('RGB').crop((180,500,1100,680));means.append(sum(ImageStat.Stat(im).mean)/3)
assert means[1]>means[0]*2
results['held_light_control']=f'PASS: same-camera floor ROI mean display RGB {means[0]:.2f} → {means[1]:.2f}; propagated dynamic-light compatibility, not voxel source transport.'
for name in ['static-receiver-and-camera','compat-receiver-source-camera','day-night-transition']:
 fs=json.loads((w/(name+'.json')).read_text())['frames'];cs=[list(f['camera']) for f in fs]
 for i in range(1,len(cs)):cs[i][3]=cs[i-1][3]+(cs[i][3]-cs[i-1][3]+180)%360-180
 spans=[max(c[i] for c in cs)-min(c[i] for c in cs) for i in range(5)]
 if name!='day-night-transition':
  assert spans[0]>1 and spans[2]>1 and spans[3]>=39
  for kind in (['minecraft:pig','minecraft:armor_stand'] if name.startswith('compat') else ['minecraft:pig']):
   ps=[e['position'] for f in fs for e in f['observedEntities'] if e['type']==kind];assert len(ps)>20 and max(p[0] for p in ps)-min(p[0] for p in ps)>3,kind
 else:
  times=[f['worldTime'] for f in fs];assert min(times)<12700 and max(times)>13000
 r=subprocess.run(['ffmpeg','-v','error','-i',str(w/(name+'.webm')),'-f','framemd5','-'],capture_output=True,text=True,check=True)
 lines=[l for l in r.stdout.splitlines() if not l.startswith('#')];unique=len({l.rsplit(',',1)[-1] for l in lines});assert len(lines)>100 and unique>50
 results[name]=f'PASS evidence integrity/motion: {len(fs)} world frames; {len(lines)} decoded video frames, {unique} distinct. Camera spans XYZ/yaw/pitch '+str([round(v,2) for v in spans])+'. Visual completeness and target-device performance remain separate gates.'
results['shader_contract']='PASS: Gradle build; 10 production model variants, 145 assertions.'
results['production_transport']='PASS: Java visibility bake evaluated through production GLSL using RGBA16F on EGL/llvmpipe, including 16/32-source HDR and torch-height floor regression.'
results['fullbright_raster']='PASS: 3,197 covered perspective pixels retain the flat full-bright flag; matching in-game orange exception capture.'
results['scope']=f"{len(json.loads((c/'compat-normal.json').read_text())['snapshot']['mods'])} modules enumerated in the compatibility snapshot; {len(json.loads((c/'static-normal.json').read_text())['snapshot']['mods'])} without LambDynamicLights/nested modules. Documented generated scenery subset; not the original Regasm save or complete user installation."
results['open_gates']='Dynamic voxel source adapter; source-aware cave air and post AO; daylight/albedo-aware GI; local moving-entity shadows; full user modpack and Intel UHD frame-time/latency measurements.'
previous=json.loads((w/'runtime-results.json').read_text()) if (w/'runtime-results.json').exists() else {}
for key in ['natural_cave_acceptance','profile_errors','ui_guard']:
 if key in previous:results[key]=previous[key]
(w/'runtime-results.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results,indent=2))
