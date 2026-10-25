#!/usr/bin/env python3
"""Production Java visibility bake + actual production GLSL on an EGL context, with numeric controls.
Requires moderngl/glcontext (optional local tooling), a JDK and an EGL OpenGL 3.3 driver.
"""
from pathlib import Path
import os,subprocess,tempfile,sys,json,struct,math
root=Path(__file__).resolve().parents[1]
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
os.environ.setdefault("MESA_SHADER_CACHE_DIR", str(root/"build/mesa-shader-cache"))
import moderngl
java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin'
with tempfile.TemporaryDirectory(prefix='aurum-incident-') as temp:
 p=Path(temp)
 for name in ['VoxelLightBaker','IncidentLightBaker']:
  (p/(name+'.java')).write_text((root/f'src/main/java/dev/impeccable/aurum/bake/{name}.java').read_text().replace('package dev.impeccable.aurum.bake;',''))
 (p/'ExportIncident.java').write_bytes((root/'tools/ExportIncident.java').read_bytes())
 subprocess.run([str(java/'javac'),*map(str,p.glob('*.java'))],check=True)
 subprocess.run([str(java/'java'),'-cp',str(p),'ExportIncident',str(p)],check=True)
 ctx=moderngl.create_standalone_context(require=330,backend='egl')
 source=(root/'src/main/resources/assets/aurum/shaders/include/voxel_light.glsl').read_text()
 program=ctx.program(vertex_shader='#version 330\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2-1,0,1);}',fragment_shader='#version 330\nuniform vec4 u_TerrainParams;uniform vec4 u_PixelLight;uniform vec3 testPosition;uniform vec3 testNormal;out vec4 fragColor;\n'+source+'\nvoid main(){fragColor=aurum_voxelLight(testPosition,testNormal);}')
 vao=ctx.vertex_array(program,[]); target=ctx.texture((1,1),4,dtype='f4');fbo=ctx.framebuffer([target]);fbo.use();ctx.viewport=(0,0,1,1)
 def draw(name,position=(2.5,3.5,4.5),normal=(0,1,0),bounce=0,origin=(0,0,0),camera=(4,4,4),crisp=0,zero=False):
  data=json.loads((p/(name+'.json')).read_text());raw=(p/(name+'.bin')).read_bytes();half=struct.pack(str(len(raw)//4)+'e',*struct.unpack(str(len(raw)//4)+'f',raw));tex=ctx.texture((data['width'],data['height']),4,half,dtype='f2');tex.filter=(moderngl.LINEAR,moderngl.LINEAR);tex.use(0)
  program['u_VoxelLight']=0;program['u_VoxelParams']=(8,data['probeSide'],0,1);program['u_VoxelOrigin']=tuple(origin[i]-camera[i] for i in range(3));program['u_TerrainParams']=(0,0,0,bounce);program['u_PixelLight']=(crisp,16,0,0)
  program['testPosition']=tuple(position[i]-camera[i] for i in range(3));program['testNormal']=normal
  points=[];colors=[]
  if zero:data['sources']=[]
  for s in data['sources']:points.extend(s[:4]);colors.extend(s[4:])
  capacity=(data['height']//data['probeSide']-1)*4;points+= [0.]*(capacity*4-len(points));colors+=[0.]*(capacity*3-len(colors))
  program['u_LocalLightPosLevel'].write(struct.pack(str(capacity*4)+'f',*points));program['u_LocalLightColor'].write(struct.pack(str(capacity*3)+'f',*colors));program['u_LocalLightCount']=len(data['sources'])
  vao.render(vertices=3);v=struct.unpack('4f',fbo.read(components=4,dtype='f4'));tex.release();assert all(math.isfinite(x) and x>=0 for x in v),v;return v
 def close(a,b,tol=2e-5):assert all(abs(x-y)<tol for x,y in zip(a,b)),(a,b)
 assert max(draw('floor',bounce=1,zero=True)[:3])==0,'complete zero-source field reused stale texture radiance'
 a=draw('open');distance=math.sqrt(17);level=(14-distance)/15;expected=level/(4-3*level)/distance
 close(a[:3],(expected,expected*.2,expected*.05))
 assert max(draw('open',normal=(0,-1,0))[:3])==0,'back face receives direct light'
 assert max(draw('open',bounce=1)[:3])==max(a[:3]),'empty air invented bounce'
 assert max(draw('wall',normal=(1,0,0),bounce=1)[:3])==0,'sealed wall leaked'
 floor=draw('floor',position=(2.5,3.,4.5));assert floor[0]>.05,'floor lost angular lighting at torch height'
 assert draw('floor',position=(2.5,3.,4.5),bounce=1)[0]>floor[0],'illuminated floor supplied no local bounce'
 warm=draw('open');cool=draw('cool');mixed=draw('mixed');close(mixed[:3],tuple(warm[i]+cool[i] for i in range(3)))
 one=draw('open',position=(5.5,4.5,4.5),normal=(1,0,0));many=draw('sixteen',position=(5.5,4.5,4.5),normal=(1,0,0));close(many[:3],tuple(x*16 for x in one[:3]));assert many[0]>1.5,'HDR source overlap clipped'
 thirtytwo=draw('thirtytwo',position=(5.5,4.5,4.5),normal=(1,0,0));close(thirtytwo[:3],tuple(x*32 for x in one[:3]))
 assert draw('open',position=(6.5,4.5,4.5),normal=(1,0,0))[0]>.5,'emitter centre became a dark hole'
 before=draw('origin0',position=(5.5,3.5,3.5),normal=(0,1,0),bounce=1)
 after=draw('origin4',position=(5.5,3.5,3.5),normal=(0,1,0),bounce=1,origin=(4,0,0));close(before,after)
 close(draw('origin0',position=(5.5,3.5,3.5),bounce=1,crisp=1),draw('origin4',position=(5.5,3.5,3.5),bounce=1,origin=(4,0,0),crisp=1))
 assert max(draw('wall',normal=(1,0,0),bounce=1,crisp=1)[:3])==0,'crisp visibility leaked through wall'
 high=draw('high',position=(2.5,3.,4.5));assert high[0]>.05,'half-block field lost floor light'
 print('Actual production GLSL + Java bake passed: analytic receiver lighting, dark back face, empty-air control, wall occlusion, torch-height floor regression, local bounce, mixed hues, sixteen/thirty-two-source linear HDR, emitter centre, recenter radiance/coverage and half-block layout.')
 print('Validation renderer:',ctx.info['GL_RENDERER'],'(not an Intel UHD performance test)')
