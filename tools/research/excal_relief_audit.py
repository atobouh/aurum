#!/usr/bin/env python3
"""Research-only asset inventory and execution of Aurum's actual generated-height baker.
No renderer changes. The pack remains local; only counts/measurements are emitted.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,zipfile,sys
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
from PIL import Image
import io
parser=argparse.ArgumentParser();parser.add_argument('--pack',type=Path,required=True);parser.add_argument('--minecraft-client',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=True)
root=Path(__file__).resolve().parents[2];src=root/'src/main/java/dev/impeccable/aurum/bake/AtlasBaker.java';source=src.read_text();start=source.index('    private static void bakeGeneratedHeight(');opening=source.index('{',start);depth=1;end=opening+1
while depth:
 if source[end]=='{':depth+=1
 elif source[end]=='}':depth-=1
 end+=1
method=source[start:end]
records=[]
with zipfile.ZipFile(args.pack) as z:
 names=z.namelist();models=[]
 for n in names:
  if '/models/block/' in n and n.endswith('.json'):
   try:d=json.loads(z.read(n));models.append((n,d))
   except json.JSONDecodeError:pass
 inventory={'zipSha256':hashlib.sha256(args.pack.read_bytes()).hexdigest(),'packMetadata':json.loads(z.read('pack.mcmeta')),'entries':len(names),'pngs':sum(n.lower().endswith('.png') for n in names),'labNormalMaps':sum(n.lower().endswith('_n.png') for n in names),'labSpecularMaps':sum(n.lower().endswith('_s.png') for n in names),'shaderFiles':sum('/shaders/' in n and not n.endswith('/') for n in names),'blockModels':len(models),'modelsDefiningElements':sum(bool(d.get('elements')) for _,d in models),'modelsWithMultipleElements':sum(len(d.get('elements',[]))>1 for _,d in models),'selectedModels':[{'file':n,'elements':len(d.get('elements',[]))} for n,d in models if n.rsplit('/',1)[-1] in ['ladder.json','bookshelf.json','rail.json']]}
 selected={}
 def model_refs(value):
  if isinstance(value,dict):
   if 'model' in value:yield value['model']
   for v in value.values():yield from model_refs(v)
  elif isinstance(value,list):
   for v in value:yield from model_refs(v)
 for block in ['stone_bricks','oak_planks','cobblestone','bookshelf']:
  seen=set();textures={};state=f'assets/minecraft/blockstates/{block}.json'
  refs=set(model_refs(json.loads(z.read(state)))) if state in names else {'block/'+block}
  def visit(model):
   if model in seen:return
   seen.add(model);ns,path=model.split(':',1) if ':' in model else ('minecraft',model);n=f'assets/{ns}/models/{path}.json'
   if n not in names:return
   d=json.loads(z.read(n))
   if 'parent' in d:visit(d['parent'])
   for tex in d.get('textures',{}).values():
    if tex.startswith('#'):continue
    ns,path=tex.split(':',1) if ':' in tex else ('minecraft',tex);n=f'assets/{ns}/textures/{path}.png'
    if n in names:textures[n]=list(Image.open(io.BytesIO(z.read(n))).size)
  for model in sorted(refs):visit(model)
  selected[block]=textures
 inventory['selectedModelTextureReferences']=selected
 for name in ['stone_bricks','oak_planks','gravel','sandstone','white_concrete']:
  path=f'assets/minecraft/textures/block/{name}.png'
  if path in names:
   im=Image.open(io.BytesIO(z.read(path))).convert('RGBA');records.append(('excal-'+name,im))
 for name in ['stonebrick1','cobblestone1','planks/oak_planks_atlas']:
  path=f'assets/minecraft/textures/block/{name}.png'
  im=Image.open(io.BytesIO(z.read(path))).convert('RGBA');records.append(('excal-model-'+name.replace('/','-'),im))
with zipfile.ZipFile(args.minecraft_client) as z:
 for name in ['stone_bricks','oak_planks','gravel','sandstone','white_concrete']:
  im=Image.open(io.BytesIO(z.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA');records.append(('vanilla-'+name,im))
def pattern(center,outer,alpha=255):
 im=Image.new('RGBA',(16,16),(*outer,255))
 for y in range(5,11):
  for x in range(5,11):im.putpixel((x,y),(*center,alpha))
 return im
records.extend([('synthetic-painted-dark-center',pattern((40,40,40),(190,190,190))),('synthetic-painted-bright-center',pattern((190,190,190),(40,40,40))),('synthetic-flat-color',pattern((128,128,128),(128,128,128))),('synthetic-one-transparent-texel',pattern((128,128,128),(190,190,190)))])
records[-1][1].putpixel((0,0),(0,0,0,0))
java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin'
wrapper='''import java.nio.*;import java.nio.file.*;public class HeightAudit{METHOD
public static void main(String[] a)throws Exception{int w=Integer.parseInt(a[2]),h=Integer.parseInt(a[3]);ByteBuffer src=ByteBuffer.wrap(Files.readAllBytes(Path.of(a[0])));ByteBuffer pbr=ByteBuffer.allocate(w*h*4);for(int i=0;i<w*h;i++)pbr.put(i*4+3,(byte)255);bakeGeneratedHeight(src,pbr,w,1,0,0,w,h);byte[] out=new byte[w*h];for(int i=0;i<out.length;i++)out[i]=pbr.get(i*4+3);Files.write(Path.of(a[1]),out);}}'''.replace('METHOD',method)
results={}
with tempfile.TemporaryDirectory(prefix='aurum-height-audit-') as tmp:
 p=Path(tmp);(p/'HeightAudit.java').write_text(wrapper);subprocess.run([str(java/'javac'),str(p/'HeightAudit.java')],check=True)
 for name,im in records:
  w,h=im.size;(p/'input.bin').write_bytes(im.tobytes());subprocess.run([str(java/'java'),'-cp',str(p),'HeightAudit',str(p/'input.bin'),str(p/'height.bin'),str(w),str(h)],check=True);values=list((p/'height.bin').read_bytes());center=[values[y*w+x] for y in range(5,min(11,h)) for x in range(5,min(11,w))];outside=[v for i,v in enumerate(values) if not (5<=i%w<11 and 5<=i//w<11)]
  luminance=[(.2126*r+.7152*g+.0722*b)/255 for r,g,b,a in im.getdata()];results[name]={'inputLuminanceRange':max(luminance)-min(luminance),'width':w,'height':h,'minHeightByte':min(values),'maxHeightByte':max(values),'rangeBlocksAtDepth100':(max(values)-min(values))/255*.22,'uniqueHeights':len(set(values)),'centerMinusOutside':sum(center)/len(center)-sum(outside)/len(outside)}
  if name.startswith('synthetic-'):results[name]['middleRow']=values[(h//2)*w:(h//2+1)*w]
assert results['synthetic-flat-color']['uniqueHeights']==1
assert results['synthetic-one-transparent-texel']['minHeightByte']==255
assert results['synthetic-painted-dark-center']['centerMinusOutside']<0
assert results['synthetic-painted-bright-center']['centerMinusOutside']>0
report={'scope':'Research controls: exact generated-height Java method extracted unchanged from the production source; not a Minecraft rendering or Intel UHD benchmark. Synthetic painted squares have no physical geometry; any inferred depth there is a false positive by construction.','sourceFile':str(src.relative_to(root)),'sourceSha256':hashlib.sha256(src.read_bytes()).hexdigest(),'methodSha256':hashlib.sha256(method.encode()).hexdigest(),'pack':inventory,'heightMeasurements':results,'controls':'PASS: constant/transparent safe fallback; painted flat motifs demonstrate false depth and polarity ambiguity.'}
(args.out/'asset-height-audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
