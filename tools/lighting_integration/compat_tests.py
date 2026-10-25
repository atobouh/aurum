from static_tests import *

def config(**kw):
 c=json.loads(CFG.read_text());c.update(kw);CFG.write_text(json.dumps(c,indent=2)+'\n');cmd('aurumdev reload')
def clean():
 for tag in ['aurum_light_receiver','aurum_light_source','aurum_fullbright']:cmd('kill @e[tag='+tag+']')
 cmd('item replace entity @s weapon.mainhand with minecraft:air')

if __name__=='__main__':
 config(pomEnabled=False,labPbr=False,cloudsEnabled=False,wavingEnabled=False,bounceEnabled=True,bounceStrength=100,voxelDetail=4,lightEdgeStyle='NATURAL')
 clean();cmd('gamemode spectator');cmd('tp @s -1250.5 -42 4121.5 180 8');cmd('time set 1350000');cmd('setblock -1254 -43 4118 minecraft:torch');time.sleep(6)
 cap('compat-normal','off');cap('compat-paths','path')
 state=json.loads((OUT/'compat-normal.json').read_text())['snapshot'];assert state['voxel']['dynamicCompatibilityFallback'] and not state['voxel']['usable']
 cmd('setblock -1254 -43 4118 minecraft:air');cmd('setblock -1248 -43 4118 minecraft:air');cmd('setblock -1251 -43 4117 minecraft:furnace[facing=south,lit=false]');cmd('gamemode creative');cmd('tp @s -1250.5 -43 4121.5 180 8');cmd('time set 1362000');time.sleep(8);cap('compat-unlit','off')
 cmd('item replace entity @s weapon.mainhand with minecraft:torch');time.sleep(8);cap('compat-held','off')
 cmd('item replace entity @s weapon.mainhand with minecraft:air');cmd('gamemode spectator');cmd('tp @s -1250.5 -42 4121.5 180 8')
 cmd('summon minecraft:pig -1250 -43 4119 {NoAI:1b,NoGravity:1b,Tags:["aurum_light_receiver"]}')
 cmd('summon minecraft:armor_stand -1251 -43 4118 {NoGravity:1b,ShowArms:1b,Invulnerable:1b,HandItems:[{id:"minecraft:torch",Count:1b},{}],Tags:["aurum_light_source"]}')
 time.sleep(6);cap('compat-moving-source','off');cmd('aurumdev lighting off');recording('compat-receiver-source-camera',20)
 clean();config(pomEnabled=True,labPbr=False,cloudsEnabled=True)
 cmd('tp @s 2583.5 111 3328.5 90 8');cmd('time set 1350000');time.sleep(22);cap('outdoor-noon','off')
 cmd('time set 1356000');time.sleep(6);cap('outdoor-sunset','off')
 cmd('time set 1362000');time.sleep(6);cap('outdoor-night','off')
 cmd('time set 12650');cmd('gamerule doDaylightCycle true');cmd('aurumdev lightrecord start');cmd('hud hidden')
 video=WORK/'day-night-transition.webm'
 proc=subprocess.Popen(['ffmpeg','-y','-loglevel','error','-f','x11grab','-framerate','15','-video_size','1280x720','-i',ENV['DISPLAY'],'-t','30','-c:v','libvpx-vp9','-cpu-used','8','-threads','2','-crf','36','-b:v','0',str(video)],env=ENV)
 proc.wait(timeout=45);assert proc.returncode==0;cmd('gamerule doDaylightCycle false');cmd('aurumdev lightrecord stop')
 source=max((ROOT/'run/debug/aurum-lighting').glob('motion-*.json'),key=lambda p:p.stat().st_mtime);shutil.copy2(source,WORK/'day-night-transition.json')
 cmd('tp @s -1258.5 66 4125.5 180 8');cmd('time set 1350000');time.sleep(22);cap('water-day','off')
 cmd('time set 1362000');time.sleep(6);cap('water-night','off')
 cmd('tp @s -1250.5 -42 4121.5 180 8');cmd('hud visible')
 print('COMPAT SUITE COMPLETE',flush=True)
