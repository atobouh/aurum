from pathlib import Path
import os,subprocess,time,json,hashlib,uuid
ROOT=Path(__file__).resolve().parents[2];OUT=Path(os.environ.get('AURUM_LIGHTING_EVIDENCE', str(ROOT/'scratch/lighting-evidence')))/'captures';OUT.mkdir(parents=True,exist_ok=True)
CFG=ROOT/'run/config/aurum.json'
XD=os.environ.get('AURUM_XDOTOOL','xdotool');ENV=os.environ.copy();ENV.setdefault('DISPLAY',':100')
BRIDGE=ROOT/'run/debug/aurum-test-bridge';BRIDGE.mkdir(parents=True,exist_ok=True)
def request(command):
 request_id=uuid.uuid4().hex;p=BRIDGE/'pending.json';p.write_text(json.dumps({'id':request_id,'command':command.lstrip('/')}));p.replace(BRIDGE/'request.json')
 deadline=time.monotonic()+40
 while time.monotonic()<deadline:
  try:
   r=json.loads((BRIDGE/'response.json').read_text())
   if r.get('id')==request_id:
    if r.get('error'):raise RuntimeError(r)
    return r
  except (FileNotFoundError,json.JSONDecodeError):pass
  time.sleep(.05)
 raise RuntimeError('Bridge timeout: '+command)
def cmd(command,wait=.2):
 r=request(command);time.sleep(wait)
 if command.endswith('lightrecord stop'):return 'Lighting recording saved: '+r['result']
 if command.endswith('lightrecord start'):return 'Lighting recording started'
 return json.dumps(r)
def cap(name,mode,wait=.2):
 selected=request('aurumdev lighting '+mode);request('hud hidden');time.sleep(wait)
 deadline=time.monotonic()+60
 while time.monotonic()<deadline:
  r=request('aurumdev lighting snapshot');snapshot=ROOT/'run/debug/aurum-lighting'/r['result'];state=json.loads(snapshot.read_text());v=state['voxel']
  settled=state['terrainMode']==0 or v['dynamicCompatibilityFallback'] or (v['ready'] and v['origin']==v['desiredOrigin'] and v['detail']==state['config']['voxelDetail'])
  if r['frame']>=selected['frame']+2 and settled:break
  time.sleep(.3)
 else:raise RuntimeError('No settled frame/cache for '+name+' '+str(v))
 p=OUT/(name+'.png');subprocess.run(['import','-window','root',str(p)],env=ENV,check=True)
 data={'image':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'capturedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'world':'Aurum Lighting Audit (disposable generated scenery copy; fixture or natural scene identified by capture name)','commands':['aurumdev lighting '+mode,'aurumdev lighting snapshot'],'snapshot':state,'bridgeAck':r,'scope':'Actual Minecraft alpha capture; llvmpipe OpenGL 3.3, not Intel UHD. Commands executed through the development-only bridge restricted to the disposable save.'}
 p.with_suffix('.json').write_text(json.dumps(data,indent=2)+'\n');print(name,flush=True)
