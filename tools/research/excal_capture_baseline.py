from pathlib import Path
import sys,json,time,os
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools/lighting_integration'))
import capture as c
W=Path(os.environ.get('AURUM_EXCAL_EVIDENCE',str(ROOT/'scratch/excal-relief-research')));c.OUT=W/'captures';c.OUT.mkdir(exist_ok=True)
profile=sys.argv[1]
def config(**kw):
 d=json.loads(c.CFG.read_text());d.update(kw);c.CFG.write_text(json.dumps(d,indent=2)+'\n');c.cmd('aurumdev reload')
def cap(name,mode='off'):
 c.cap(profile+'-'+name,mode,wait=2)
 p=c.OUT/(profile+'-'+name+'.json');d=json.loads(p.read_text());d.update(scope='Actual Minecraft research baseline; existing unmodified renderer, LabPBR disabled. Not a newly implemented relief technique or Intel UHD performance test.',resourcePack=profile,scene='Disposable research detail board',rendererCommit='b8f5a5b17d64e37d45162109ae1d818dd45ce848');p.write_text(json.dumps(d,indent=2)+'\n')
c.cmd('gamemode spectator');c.cmd('tp @s -1228.5 201 4120.5 115 4');time.sleep(10)
if profile=='vanilla':
 for cmd in ['fill -1237 198 4113 -1223 208 4127 air','fill -1237 199 4113 -1223 199 4127 smooth_stone','fill -1234 200 4115 -1234 203 4116 stone_bricks','fill -1234 200 4117 -1234 203 4118 oak_planks','fill -1234 200 4119 -1234 203 4120 cobblestone','fill -1234 200 4121 -1234 203 4122 bookshelf'] :c.cmd(cmd)
c.cmd('time set 3000');c.cmd('gamerule doDaylightCycle false');c.cmd('weather clear');c.cmd('effect clear @s minecraft:night_vision');c.cmd('item replace entity @s weapon.mainhand with air')
config(enabled=True,labPbr=False,pomGenerated=True,pomDepth=100,pomDistance=16,normalStrength=50,sharpness=0,wavingEnabled=False,cloudsEnabled=False,normalsEnabled=False,pomEnabled=False)
time.sleep(8);cap('flat')
config(normalsEnabled=True,pomEnabled=False);cap('normals')
config(pomEnabled=True);cap('relief')
config(sharpness=65);cap('relief-sharp65')
c.cmd('tp @s -1231.5 201 4117.5 115 4');time.sleep(8);cap('close')
if profile=='excal':
 c.cmd('tp @s -1228.5 201 4120.5 115 4');config(enabled=False);cap('native-models');config(enabled=True)
c.request('stop client');print('RESEARCH BASELINE COMPLETE '+profile,flush=True)
