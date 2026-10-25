#!/usr/bin/env python3
"""Characterize current local-light ownership gaps using the production baker, not a replacement model.
These assertions describe current behaviour; they are NOT acceptance tests for unified lighting.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin'
with tempfile.TemporaryDirectory(prefix='aurum-light-contract-') as temp:
    p = Path(temp)
    source = root / 'src/main/java/dev/impeccable/aurum/bake/VoxelLightBaker.java'
    (p / 'VoxelLightBaker.java').write_text(source.read_text().replace('package dev.impeccable.aurum.bake;', ''))
    (p / 'CheckContract.java').write_text(r'''
import java.util.*;
public class CheckContract {
    static final int SIDE=12, FIELD=8, MARGIN=2, DETAIL=4;
    static final int[] ids=new int[SIDE*SIDE*SIDE];
    static final byte[] emission=new byte[ids.length];
    static final List<float[]> shapes=List.of(new float[0],new float[]{0,0,0,1,1,1});
    static final VoxelLightBaker.Light front=new VoxelLightBaker.Light(9.5f,4.5f,5.5f,14,1,.2f,.05f);
    static final VoxelLightBaker.Light back=new VoxelLightBaker.Light(2.5f,4.5f,5.5f,14,1,.2f,.05f);
    static VoxelLightBaker.Result bake(List<VoxelLightBaker.Light> lights) {
        return VoxelLightBaker.bake(new VoxelLightBaker.Input(SIDE,FIELD,MARGIN,DETAIL,ids,emission,shapes,lights),()->false);
    }
    static int sample(VoxelLightBaker.Result r,int face) {
        int tile=(((2*FIELD+3)*FIELD+2)*6)+face;
        int tx=(tile%(r.width()/DETAIL))*DETAIL,ty=(tile/(r.width()/DETAIL))*DETAIL;
        return ((ty+1)*r.width()+tx+1)*4;
    }
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args) {
        int receiver=VoxelLightBaker.index(SIDE,4,4,5); ids[receiver]=1;
        var one=bake(List.of(front)); int forward=sample(one,0),rear=sample(one,1);
        require(one.rgba()[forward]>0,"positive directly lit receiver control");
        require(one.rgba()[rear]==0 && one.rgba()[rear+3]==1,"back face is valid zero: no indirect term");
        var capped=bake(Collections.nCopies(16,back));
        require(capped.rgba()[sample(capped,0)]==0 && capped.rgba()[sample(capped,0)+3]==1,
            "supplied bounded source set may publish valid darkness");
        var all=new ArrayList<VoxelLightBaker.Light>(Collections.nCopies(16,back));all.add(front);
        var complete=bake(all);
        require(complete.rgba()[sample(complete,0)]>0,"omitted source restores energy if supplied");
        ids[receiver]=0;var card=bake(List.of(front));
        require(card.faces()==0,"noncolliding receivers have no local-light coverage");
        ids[receiver]=1;emission[receiver]=14;var emitter=bake(List.of(front));
        require(emitter.faces()==0,"emitting receivers retain fallback instead of receiving atlas light");
        require(bake(List.of()).rgba().length==0,"no-source field unavailable, not a valid black field");
        System.out.println("Lighting characterization passed (7 behaviours): positive direct control, valid dark back face, bounded-set darkness, omitted-source energy, noncollision fallback, emitter fallback, no-source coverage.");
        System.out.println("Known gaps reproduced, not fixed. Source-set test supplies 16/17 sources; runtime ranking and dynamic-light injection are outside this harness.");
    }
}
''')
    subprocess.run([str(java / 'javac'), str(p / 'VoxelLightBaker.java'), str(p / 'CheckContract.java')], check=True)
    subprocess.run([str(java / 'java'), '-cp', str(p), 'CheckContract'], check=True)
