import java.util.*;
import java.nio.*;
import java.nio.file.*;
public class ExportIncident {
 static final int N=16,S=8,M=4;
 static final List<float[]> SHAPES=List.of(new float[0],new float[]{0,0,0,1,1,1});
 static VoxelLightBaker.Light warm=new VoxelLightBaker.Light(10.5f,8.5f,8.5f,14,1,.2f,.05f);
 static VoxelLightBaker.Light cool=new VoxelLightBaker.Light(10.5f,8.5f,8.5f,14,.24f,.72f,1);
 static void save(Path dir,String name,int[] ids,List<VoxelLightBaker.Light> lights,int detail) throws Exception {
  var result=IncidentLightBaker.bake(new VoxelLightBaker.Input(N,S,M,detail,ids,new byte[ids.length],SHAPES,lights),()->false);
  if(result==null)throw new AssertionError("cancelled fixture");
  var bytes=ByteBuffer.allocate(result.rgba().length*4).order(ByteOrder.LITTLE_ENDIAN);
  for(float f:result.rgba()){if(!Float.isFinite(f)||f<0)throw new AssertionError("invalid probe");bytes.putFloat(f);}
  Files.write(dir.resolve(name+".bin"),bytes.array());
  var json=new StringBuilder("{\"width\":"+result.width()+",\"height\":"+result.height()+",\"probeSide\":"+(detail==8?16:8)+",\"sources\":[");
  for(int i=0;i<lights.size();i++){if(i>0)json.append(',');var l=lights.get(i);json.append('[').append(l.x()-M).append(',').append(l.y()-M).append(',').append(l.z()-M).append(',').append(l.level()).append(',').append(l.red()).append(',').append(l.green()).append(',').append(l.blue()).append(']');}
  json.append("]}");Files.writeString(dir.resolve(name+".json"),json);
 }
 public static void main(String[] args)throws Exception {
  if(!IncidentLightBaker.couldContribute(-19,8,8,16,15)||IncidentLightBaker.couldContribute(-19.01f,8,8,16,15))throw new AssertionError("source influence bound");
  if(!IncidentLightBaker.couldContribute(35,8,8,16,15)||IncidentLightBaker.couldContribute(35.01f,8,8,16,15))throw new AssertionError("far-side influence bound");
  Path dir=Path.of(args[0]);int[] ids=new int[N*N*N];
  save(dir,"open",ids,List.of(warm),4);save(dir,"cool",ids,List.of(cool),4);save(dir,"mixed",ids,List.of(warm,cool),4);save(dir,"sixteen",ids,Collections.nCopies(16,warm),4);save(dir,"thirtytwo",ids,Collections.nCopies(32,warm),4);
  for(int z=0;z<N;z++)for(int y=0;y<N;y++)ids[VoxelLightBaker.index(N,8,y,z)]=1;
  save(dir,"wall",ids,List.of(warm),4);
  Arrays.fill(ids,0);for(int z=0;z<N;z++)for(int x=0;x<N;x++)ids[VoxelLightBaker.index(N,x,6,z)]=1;
  save(dir,"floor",ids,List.of(warm),4);save(dir,"high",ids,List.of(warm),8);
  for(int origin:new int[]{0,4}){
   Arrays.fill(ids,0);for(int z=0;z<N;z++)for(int x=0;x<N;x++)ids[VoxelLightBaker.index(N,x,4,z)]=1;
   save(dir,"origin"+origin,ids,List.of(new VoxelLightBaker.Light(6.5f-origin+M,4.5f+M,4.5f+M,14,1,.2f,.05f)),4);
  }
  var in=new VoxelLightBaker.Input(N,S,M,4,ids,new byte[ids.length],SHAPES,List.of(warm));
  if(IncidentLightBaker.bake(in,()->true)!=null)throw new AssertionError("cancellation");
  if(IncidentLightBaker.bake(new VoxelLightBaker.Input(N,S,M,4,ids,new byte[ids.length],SHAPES,List.of()),()->false).rgba().length!=0)throw new AssertionError("empty field");
  try{IncidentLightBaker.bake(new VoxelLightBaker.Input(N,S,M,4,ids,new byte[ids.length],SHAPES,Collections.nCopies(33,warm)),()->false);throw new AssertionError("overflow accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Production incident fixture export and cancellation/empty/overflow controls passed.");
 }
}
