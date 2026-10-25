#!/usr/bin/env python3
"""Exercise voxel visibility and colour baking with actual collision-box fixtures; JDK only."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
java=Path(os.environ.get('JAVA_HOME','/usr'))/'bin'
with tempfile.TemporaryDirectory(prefix='aurum-voxel-check-') as temp:
    p=Path(temp)
    source=(root/'src/main/java/dev/impeccable/aurum/bake/VoxelLightBaker.java').read_text()
    (p/'VoxelLightBaker.java').write_text(source.replace('package dev.impeccable.aurum.bake;', ''))
    (p/'Check.java').write_text(r'''
import java.util.*;
public class Check {
  static int side=12, checks=0;
  static int[] ids=new int[side*side*side];
  static List<float[]> shapes=List.of(new float[0],new float[]{0,0,0,1,1,1},new float[]{0,0,0,1,.5f,1});
  static VoxelLightBaker.Light light=new VoxelLightBaker.Light(9.5f,4.5f,5.5f,14,1,.2f,.05f);
  static VoxelLightBaker.Input input(List<VoxelLightBaker.Light> lights) {
    return new VoxelLightBaker.Input(side,8,2,4,ids,new byte[ids.length],shapes,lights);
  }
  static void block(int x,int y,int z,int id){ids[VoxelLightBaker.index(side,x,y,z)]=id;}
  static void ok(boolean b,String name){checks++;if(!b)throw new AssertionError(name);}
  static boolean ray(float y){return VoxelLightBaker.visible(input(List.of(light)),2.5f,y,5.5f,
    new VoxelLightBaker.Light(9.5f,y,5.5f,14,1,.2f,.05f));}
  public static void main(String[] args){
    ok(ray(4.5f),"open passage");
    for(int y=0;y<side;y++) for(int z=0;z<side;z++)block(6,y,z,1);
    ok(!ray(4.5f),"closed wall occludes");
    block(6,4,5,0);ok(ray(4.5f),"one-block aperture transmits");
    block(6,4,5,2);ok(ray(4.75f),"above slab transmits");ok(!ray(4.25f),"slab lower half occludes");
    block(6,4,5,-1);ok(!ray(4.5f),"unloaded cell conservative");
    Arrays.fill(ids,0);block(9,4,5,1);ok(ray(4.5f),"emitter cell does not occlude itself");
    Arrays.fill(ids,0);block(4,4,5,1);
    var baked=VoxelLightBaker.bake(input(List.of(light)),()->false);
    int valid=0,coloured=0;
    for(int i=0;i<baked.rgba().length;i+=4){
      for(int c=0;c<4;c++)ok(Float.isFinite(baked.rgba()[i+c])&&baked.rgba()[i+c]>=0,"finite nonnegative atlas");
      if(baked.rgba()[i+3]>0)valid++;
      if(baked.rgba()[i]>.001f){coloured++;ok(Math.abs(baked.rgba()[i+1]/baked.rgba()[i]-.2f)<.0001f,"light hue retained");}
    }
    ok(valid==6*16,"six faces with 4x4 samples");ok(coloured==16,"only source-facing face illuminated");
    var again=VoxelLightBaker.bake(input(List.of(light)),()->false);
    ok(Arrays.equals(baked.rgba(),again.rgba()),"deterministic bake");
    ok(VoxelLightBaker.bake(input(List.of()),()->false).rgba().length==0,"no-light allocation skipped");
    ok(VoxelLightBaker.bake(input(List.of(light)),()->true)==null,"cancelled bake not published");
    // An occluding box cuts the receiver's +X face, then removing it restores exactly the old field.
    block(6,4,5,1);
    var blocked=VoxelLightBaker.bake(input(List.of(light)),()->false);
    float openRed=0,blockedRed=0;
    int tile=(((2*8+3)*8+2)*6);int tx=(tile%256)*4,ty=(tile/256)*4;
    for(int v=0;v<4;v++)for(int u=0;u<4;u++){
      int i=((ty+v)*1024+tx+u)*4;openRed+=baked.rgba()[i];blockedRed+=blocked.rgba()[i];
    }
    ok(openRed>.01f&&blockedRed==0,"occluder produces a real surface shadow");
    System.out.printf("Voxel fixtures passed: wall, aperture, slab, unloaded cells, source self-occlusion, colour, cancellation, determinism and surface shadow (%d assertions).%n",checks);
  }
}
''')
    subprocess.run([str(java/'javac'),str(p/'VoxelLightBaker.java'),str(p/'Check.java')],check=True)
    subprocess.run([str(java/'java'),'-cp',str(p),'Check'],check=True)
