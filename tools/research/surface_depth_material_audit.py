#!/usr/bin/env python3
"""Measure the public reference pack's material channels without exporting its textures.
The selected R5 maps explain a technique; they are not a verified match to the trailer's revision.
"""
from pathlib import Path
import argparse,collections,hashlib,io,json,math,sys,zipfile
local=Path('/workspace/test-tools/python')
if local.exists():sys.path.insert(0,str(local))
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--reference-pack',type=Path,required=True);p.add_argument('--minecraft-client',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
selected=['block/oak_log_top','block/oak_log','block/crafting_table_top','block/blue_wool','entity/chest/ender'];rows=[]
with zipfile.ZipFile(a.reference_pack) as pack,zipfile.ZipFile(a.minecraft_client) as mc:
 names=pack.namelist()
 for key in selected:
  stem='assets/minecraft/textures/'+key;base=Image.open(io.BytesIO(pack.read(stem+'.png'))).convert('RGBA');normal=Image.open(io.BytesIO(pack.read(stem+'_n.png'))).convert('RGBA');native=Image.open(io.BytesIO(mc.read(stem+'.png')));assert base.size==normal.size
  bp=list(base.getdata());np=list(normal.getdata());visible=[(b,n) for b,n in zip(bp,np) if b[3]>=250];assert visible,key;lum=[(.2126*r+.7152*g+.0722*b)/255 for (r,g,b,_),_ in visible];heights=[n[3] for _,n in visible];lm=sum(lum)/len(lum);hm=sum(heights)/len(heights);cov=sum((x-lm)*(y-hm) for x,y in zip(lum,heights));den=math.sqrt(sum((x-lm)**2 for x in lum)*sum((y-hm)**2 for y in heights));corr=cov/den if den else None
  groups=collections.defaultdict(list)
  for b,n in visible:groups[b[:3]].append(n[3])
  ambiguous=sum(len(v) for v in groups.values() if min(v)!=max(v));w,h=base.size;cell=w//native.width;assert cell>0 and w==native.width*cell and h==native.height*cell
  constant=0;varying=0;total=0
  for y in range(0,h,cell):
   for x in range(0,w,cell):
    pairs=[(bp[Y*w+X],np[Y*w+X]) for Y in range(y,min(y+cell,h)) for X in range(x,min(x+cell,w)) if bp[Y*w+X][3]>=250]
    if not pairs:continue
    constant+=len({b[:3] for b,n in pairs})==1;varying+=len({n[:2] for b,n in pairs})>1;total+=1
  rows.append({'texture':key,'resolution':list(base.size),'vanillaResolution':list(native.size),'scaleOverVanilla':cell,'opaquePixelFraction':len(visible)/len(bp),'heightByteRange':[min(heights),max(heights)],'heightLevels':len(set(heights)),'luminanceHeightCorrelation':corr,'fractionPixelsSharingColorWithDifferentHeight':ambiguous/len(visible),'constantColorCellFraction':constant/total,'varyingNormalCellFraction':varying/total})
 report={'reference':'Official public AVPBR Retextured R5 demo, Modrinth HC44ldY7','sha256':hashlib.sha256(a.reference_pack.read_bytes()).hexdigest(),'scope':'Opaque colour texels only (alpha >=250), excluding unused transparent skin space. R5 is not established as the version in the trailer. Only dimensions/counts/statistics are exported, not copyrighted texture maps.','brightnessDefinition':'Rec.709 weights applied to stored RGB/255, matching the current Aurum heuristic; this is not a linear-light luminance measurement.','normalMaps':sum(n.lower().endswith('_n.png') for n in names),'specularMaps':sum(n.lower().endswith('_s.png') for n in names),'materials':rows}
 assert len(rows)==5 and all(r['heightLevels']>1 for r in rows)
 assert rows[0]['luminanceHeightCorrelation']<0 and rows[1]['luminanceHeightCorrelation']>0,'The documented opposite polarity control changed'
 (a.out/'reference-material-study.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
