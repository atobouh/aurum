#!/usr/bin/env python3
"""Capture a running dev client on X11; stores original PNG and reproducibility sidecar.

Example: python tools/capture_visual_lab.py /tmp/noon.png --set skyClarity=75 \
  --command '/time set 6000' --wait 20
Requires xdotool/ImageMagick on PATH (or XDOTOOL), and the dev-only /aurumdev reload command.
Use tools/visual_lab fixtures in a disposable world; never install the fixture datapack in a real save.
"""
import argparse,hashlib,json,os,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('output',type=Path)
p.add_argument('--set',action='append',default=[])
p.add_argument('--command',action='append',default=[])
p.add_argument('--wait',type=float,default=20)
a=p.parse_args();r=Path(__file__).resolve().parents[1]
cfg=r/'run/config/aurum.json';c=json.loads(cfg.read_text())
xd=os.environ.get('XDOTOOL','/workspace/test-tools/usr/bin/xdotool')
env=os.environ.copy();env.setdefault('DISPLAY',':100');env.setdefault('LD_LIBRARY_PATH','/workspace/test-tools/usr/lib/x86_64-linux-gnu')
def key(k):subprocess.run([xd,'key',k],env=env,check=True)
def command(cmd):
    key('t');time.sleep(1.5)
    subprocess.run([xd,'type','--clearmodifiers','--delay','45',cmd],env=env,check=True)
    key('Return');time.sleep(2)
if a.set:
    for setting in a.set:
        k,v=setting.split('=',1);assert k in c,k;c[k]=json.loads(v)
    cfg.write_text(json.dumps(c,indent=2)+'\n');command('/aurumdev reload')
for cmd in a.command:command(cmd)
time.sleep(a.wait)
subprocess.run(['/usr/bin/import','-window','root',str(a.output)],env=env,check=True)
meta={'image':a.output.name,'sha256':hashlib.sha256(a.output.read_bytes()).hexdigest(),
      'capturedUtc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'commands':a.command,'settleSeconds':a.wait,
      'config':json.loads(cfg.read_text()),'renderer':'llvmpipe / Mesa software OpenGL 3.3; not an Intel UHD benchmark'}
a.output.with_suffix('.json').write_text(json.dumps(meta,indent=2)+'\n');print(a.output)
