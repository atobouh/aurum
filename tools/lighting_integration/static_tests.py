from capture import *
import shutil
WORK=OUT.parent

def recording(name,seconds=20):
 print(cmd('/aurumdev lightrecord start')[-120:],flush=True)
 cmd('/aurumdev lightmotion start')
 cmd('hud hidden')
 video=WORK/(name+'.webm')
 proc=subprocess.Popen(['ffmpeg','-y','-loglevel','error','-f','x11grab','-framerate','15','-video_size','1280x720','-i',ENV['DISPLAY'],'-t',str(seconds),'-c:v','libvpx-vp9','-cpu-used','8','-threads','2','-crf','36','-b:v','0',str(video)],env=ENV)
 time.sleep(4)
 for key,duration in [('d',.3),('a',.3),('w',.3),('s',.3)]:
  subprocess.run([XD,'keydown',key],env=ENV,check=True);time.sleep(duration);subprocess.run([XD,'keyup',key],env=ENV,check=True);time.sleep(1)
 for yaw in range(160,201,4):
  cmd('/tp @s ~ ~ ~ '+str(yaw)+' 8');time.sleep(.15)
 time.sleep(2)
 proc.wait(timeout=30)
 assert proc.returncode==0, 'Video capture failed'
 cmd('hud hidden')
 cmd('/aurumdev lightmotion stop');ack=cmd('/aurumdev lightrecord stop');assert 'Lighting recording saved:' in ack,ack
 source=max((ROOT/'run/debug/aurum-lighting').glob('motion-*.json'),key=lambda p:p.stat().st_mtime)
 shutil.copy2(source,WORK/(name+'.json'))
 print('recorded '+name,flush=True)

if __name__=='__main__':
 c=json.loads(CFG.read_text());c.update(pomEnabled=False,labPbr=False,bounceEnabled=True,bounceStrength=100,cloudsEnabled=False,wavingEnabled=False,entityShadows=True);CFG.write_text(json.dumps(c,indent=2)+'\n');cmd('/aurumdev reload')
 cmd('/kill @e[tag=aurum_light_receiver]');cmd('/kill @e[tag=aurum_light_source]');cmd('/tp @s -1250.5 -42 4121.5 180 8');cmd('/time set 1350000')
 time.sleep(8)
 for name,mode in [('static-normal','off'),('static-paths','path'),('static-coverage','coverage'),('static-block','block'),('static-normals','normal'),('static-sky','sky'),('static-direct','direct')]:cap(name,mode,wait=2)
 cmd('/aurumdev lighting off')
 print(cmd('/summon minecraft:pig -1250 -43 4119 {NoAI:1b,NoGravity:1b,Tags:["aurum_light_receiver"]}')[-160:],flush=True)
 recording('static-receiver-and-camera',20)
 cmd('/tp @s -1250.5 -42 4121.5 180 8');cmd('/kill @e[tag=aurum_light_receiver]');cmd('/kill @e[type=minecraft:item,distance=..20]')
 cmd('/setblock -1248 -43 4118 minecraft:soul_torch');time.sleep(6);cap('static-mixed-sources','off',wait=2)
 cmd('/time set 1362000');time.sleep(10);cap('static-midnight','off',wait=2)
 cmd('/time set 1356000');time.sleep(8);cap('static-sunset','off',wait=2)
 cmd('/time set 1350000');cmd('/fill -1255 -43 4115 -1245 -43 4116 minecraft:torch');time.sleep(8);cap('static-source-overflow','off',wait=2)
 cmd('/fill -1255 -43 4115 -1245 -43 4116 minecraft:air');time.sleep(8);cap('static-after-overflow','coverage',wait=2)
 cmd('/setblock -1254 -43 4118 minecraft:air');cmd('/setblock -1248 -43 4118 minecraft:air');cmd('/setblock -1251 -43 4117 minecraft:furnace[facing=south,lit=false]');time.sleep(8);cap('static-room-unlit','off',wait=2)
 cmd('/summon minecraft:block_display -1249.5 -42 4119 {block_state:{Name:"minecraft:gold_block"},brightness:{block:15,sky:15},Tags:["aurum_fullbright"]}');cap('static-fullbright','off',wait=2);cap('static-fullbright-path','path',wait=2)
 cmd('/effect give @s minecraft:night_vision 20 0 true');cap('static-nightvision','off',wait=2);cmd('/effect clear @s minecraft:night_vision');cmd('/kill @e[tag=aurum_fullbright]')
 cmd('/setblock -1254 -43 4118 minecraft:torch');time.sleep(6);cap('static-light-restored','off',wait=2)
 c=json.loads(CFG.read_text());c.update(lightEdgeStyle='VOXEL_CRISP');CFG.write_text(json.dumps(c,indent=2)+'\n');cmd('/aurumdev reload');cap('static-crisp','off',wait=2)
 c.update(lightEdgeStyle='NATURAL',voxelDetail=8);CFG.write_text(json.dumps(c,indent=2)+'\n');cmd('/aurumdev reload');cap('static-detail8','off',wait=2)
 c.update(voxelDetail=4);CFG.write_text(json.dumps(c,indent=2)+'\n');cmd('/aurumdev reload')
 # Exact source-count controls in an elevated sealed fixture, beyond influence of native cave emitters.
 cmd('/fill -1257 198 4113 -1243 208 4127 minecraft:stone');cmd('/fill -1256 199 4114 -1244 207 4126 minecraft:air')
 cmd('/setblock -1250 199 4122 minecraft:stone');cmd('/setblock -1250 200 4122 minecraft:zombie_head[rotation=8]');cmd('/setblock -1249 199 4122 minecraft:chest[facing=south]')
 cmd('/tp @s -1250.5 201 4124.5 180 12');cmd('/time set 1350000')
 for count in [1,16,17,32,33,0]:
  cmd('/fill -1254 199 4116 -1247 199 4120 minecraft:air')
  remaining=count
  for row in range(5):
   n=min(8,remaining)
   if n:cmd('/fill -1254 199 '+str(4116+row)+' '+str(-1255+n)+' 199 '+str(4116+row)+' minecraft:torch')
   remaining-=n
  cap('static-exact-'+str(count),'off',wait=2)
  data=json.loads((OUT/('static-exact-'+str(count)+'.json')).read_text())
  assert data['snapshot']['voxel']['publishedCandidates']==count,data['snapshot']['voxel']
 print('STATIC SUITE COMPLETE',flush=True)
