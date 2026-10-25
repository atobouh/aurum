#!/usr/bin/env python3
"""Rasterize production adapted Minecraft shaders, including perspective-varying full-bright geometry.
Run ./gradlew checkSurfaceLighting first to export the actual patched/preprocessed shader pairs.
"""
from pathlib import Path
import sys,os,struct
root=Path(__file__).resolve().parents[1]
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
os.environ.setdefault('MESA_SHADER_CACHE_DIR',str(root/'build/mesa-shader-cache'))
import moderngl
ctx=moderngl.create_standalone_context(require=330,backend='egl')
folder=root/'build/shared-shader-check';name='rendertype_entity_solid'
def preprocessed(path):
 source=path.read_text();lines=source.splitlines();first=True;out=[]
 for line in lines:
  if line.strip().startswith('#version'):
   if not first:continue
   first=False
  out.append(line)
 return '\n'.join(out)
p=ctx.program(vertex_shader=preprocessed(folder/(name+'.vsh')),fragment_shader=preprocessed(folder/(name+'.fsh')))
white=ctx.texture((16,16),4,bytes([255])*16*16*4);white.use(0);white.use(1);white.use(2)
depth=ctx.depth_texture((1,1));depth.compare_func='<=';depth.use(6)
for key in p:
 u=p[key]
 if getattr(u,'gl_type',None)==35678:u.value=0
 elif getattr(u,'gl_type',None)==35682:u.value=6
for key,index in [('Sampler0',0),('Sampler1',1),('Sampler2',2)]:
 if key in p:p[key]=index
identity=struct.pack('16f',1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)
for key in ['ModelViewMat','u_AurumInvView']:p[key].write(identity)
# Perspective w=-z differs across the triangle, exercising the interpolation that caused the stripes.
p['ProjMat'].write(struct.pack('16f',1,0,0,0,0,1,0,0,0,0,-1.020202,-1,0,0,-.2020202,0))
if 'IViewRotMat' in p:p['IViewRotMat'].write(struct.pack('9f',1,0,0,0,1,0,0,0,1))
for key in ['Light0_Direction','Light1_Direction']:
 if key in p:p[key]=(0,1,0)
p['ColorModulator']=(1,1,1,1);p['FogStart']=100;p['FogEnd']=200;p['FogColor']=(0,0,0,0)
p['u_AurumMode']=1;p['u_LightingDebug']=9
vertices=[]
for pos in [(-.9,-.8,-1),(.9,-.8,-2),(.1,.9,-1.4)]:vertices.extend(pos+(1.,1.,1.,1.,0.,0.,0,0,240,240,0.,1.,0.))
# Interleave attributes with the original integer overlay/light formats.
data=b''.join(struct.pack('9f4i3f',*vertices[i:i+16]) for i in range(0,len(vertices),16))
buf=ctx.buffer(data);vao=ctx.vertex_array(p,[(buf,'3f 4f 2f 2i 2i 3f','Position','Color','UV0','UV1','UV2','Normal')])
target=ctx.texture((128,128),4,dtype='f4');fbo=ctx.framebuffer([target]);fbo.use();ctx.viewport=(0,0,128,128);fbo.clear(0,0,0,0);vao.render(vertices=3)
values=struct.unpack('65536f',fbo.read(components=4,dtype='f4'));covered=0
for i in range(0,len(values),4):
 if values[i+3]>.5:
  covered+=1
  assert abs(values[i]-1)<1e-6 and abs(values[i+1]-.5)<1e-6 and abs(values[i+2])<1e-6,'Full-bright perspective triangle entered another lighting path'
assert covered>2000,covered
print('Production adapted core shader raster passed:',covered,'perspective-covered pixels all used the flat full-bright path. Renderer:',ctx.info['GL_RENDERER'])
