package dev.impeccable.aurum.bake;

import java.util.function.BooleanSupplier;

/** World-independent incident-light probes. Surface, model and moving receivers share these samples.
 * Per-source visibility plus a separate bounded, neutral-diffuse single-bounce estimate.
 * This is local transport, not daylight GI. Collision boxes define occlusion, not receiver geometry.
 */
public final class IncidentLightBaker {
    public static final int MAX_LIGHTS = 32;
    private static final int INDIRECT_BAND = MAX_LIGHTS / 4;
    private static final int[] NX={1,-1,0,0,0,0}, NY={0,0,1,-1,0,0}, NZ={0,0,0,0,1,-1};
    private IncidentLightBaker() {}
    /** All direct sources and the bounded four-block bounce gather must fit the snapshot. */
    public static boolean couldContribute(float x,float y,float z,int fieldSide,int level) {
        float dx=Math.max(Math.max(-x,x-fieldSide),0),dy=Math.max(Math.max(-y,y-fieldSide),0),dz=Math.max(Math.max(-z,z-fieldSide),0);
        float reach=level+4;
        return dx*dx+dy*dy+dz*dz<=reach*reach;
    }
    public static VoxelLightBaker.Result bake(VoxelLightBaker.Input in, BooleanSupplier cancelled) {
        if(in.lights().size()>MAX_LIGHTS) throw new IllegalArgumentException("Incident-light input exceeds the source budget; caller must use the overflow fallback");
        long start=System.nanoTime();
        int side=in.fieldSide()*(in.detail()==8?2:1);
        float step=(float)in.fieldSide()/side;
        int width=side*side,height=side*(INDIRECT_BAND+1);
        if(in.lights().isEmpty()) return new VoxelLightBaker.Result(width,height,in.detail(),new float[0],0,System.nanoTime()-start);
        float[] out=new float[width*height*4]; int count=0;
        for(int z=0;z<side;z++) for(int y=0;y<side;y++) for(int x=0;x<side;x++) {
            if(cancelled.getAsBoolean()) return null;
            float px=in.margin()+(x+.5f)*step,py=in.margin()+(y+.5f)*step,pz=in.margin()+(z+.5f)*step;
            if(!free(in,px,py,pz)) continue;
            count++;
            out[address(side,x,y,z,INDIRECT_BAND)+3]=1;
            for(int sourceIndex=0;sourceIndex<Math.min(MAX_LIGHTS,in.lights().size());sourceIndex++) {
                var source=in.lights().get(sourceIndex);
                float dx=source.x()-px,dy=source.y()-py,dz=source.z()-pz;
                float reach=source.level()+2*step;
                if(dx*dx+dy*dy+dz*dz>reach*reach) continue;
                if(VoxelLightBaker.visible(in,px,py,pz,source))
                    out[address(side,x,y,z,sourceIndex/4)+sourceIndex%4]=1;
            }
            // Six scene-space visibility rays gather nearby diffuse wall exitance. No screen-colour feedback.
            int ambient=address(side,x,y,z,INDIRECT_BAND);
            for(int direction=0;direction<6;direction++) {
                float distance=hit(in,px,py,pz,direction,4f);
                if(!Float.isFinite(distance)) continue;
                float hx=px+NX[direction]*distance,hy=py+NY[direction]*distance,hz=pz+NZ[direction]*distance;
                int hitIndex=VoxelLightBaker.index(in.snapshotSide(),(int)Math.floor(hx+NX[direction]*.02f),
                        (int)Math.floor(hy+NY[direction]*.02f),(int)Math.floor(hz+NZ[direction]*.02f));
                if(in.emission()[hitIndex]>0) continue; // emission is already accounted for by its source
                hx-=NX[direction]*.015f;hy-=NY[direction]*.015f;hz-=NZ[direction]*.015f;
                for(var source:in.lights()) {
                    float dx=source.x()-hx,dy=source.y()-hy,dz=source.z()-hz;
                    float d2=dx*dx+dy*dy+dz*dz;
                    if(d2<1e-8f||d2>=source.level()*source.level()) continue;
                    float d=(float)Math.sqrt(d2);
                    float cosine=Math.max(0,-(dx*NX[direction]+dy*NY[direction]+dz*NZ[direction])/d);
                    if(cosine<=0||!VoxelLightBaker.visible(in,hx,hy,hz,source)) continue;
                    float level=Math.max((source.level()-d)/15f,0);
                    // Neutral reflectance .35; bounded local gathering kernel, not an albedo-aware GI claim.
                    add(out,ambient,source,level/(4-3*level)*cosine*.35f/(6*(1+distance*distance*.25f)));
                }
            }
        }
        return new VoxelLightBaker.Result(width,height,in.detail(),out,count,System.nanoTime()-start);
    }
    public static int address(int side,int x,int y,int z,int band) {
        return ((band*side+y)*side*side+z*side+x)*4;
    }
    private static void add(float[] out,int at,VoxelLightBaker.Light l,float intensity) {
        out[at]+=l.red()*intensity;out[at+1]+=l.green()*intensity;out[at+2]+=l.blue()*intensity;
    }
    private static boolean free(VoxelLightBaker.Input in,float x,float y,float z) {
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);
        if(bx<0||by<0||bz<0||bx>=in.snapshotSide()||by>=in.snapshotSide()||bz>=in.snapshotSide()) return false;
        int id=in.shapeIds()[VoxelLightBaker.index(in.snapshotSide(),bx,by,bz)];
        if(id<0) return false;
        float[] boxes=in.shapes().get(id);
        for(int i=0;i<boxes.length;i+=6) if(x-bx>=boxes[i]&&x-bx<=boxes[i+3]&&y-by>=boxes[i+1]&&y-by<=boxes[i+4]&&z-bz>=boxes[i+2]&&z-bz<=boxes[i+5]) return false;
        return true;
    }
    /** Nearest collision-box intersection along one of six axis rays, or no gatherable surface. */
    private static float hit(VoxelLightBaker.Input in,float x,float y,float z,int direction,float range) {
        int axis=direction/2, sign=(direction&1)==0?1:-1;
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z);
        for(int k=0;k<=5;k++) {
            int cx=bx+NX[direction]*k,cy=by+NY[direction]*k,cz=bz+NZ[direction]*k;
            if(cx<0||cy<0||cz<0||cx>=in.snapshotSide()||cy>=in.snapshotSide()||cz>=in.snapshotSide()) return Float.POSITIVE_INFINITY;
            int id=in.shapeIds()[VoxelLightBaker.index(in.snapshotSide(),cx,cy,cz)];
            if(id<0) return Float.POSITIVE_INFINITY;
            float[] boxes=in.shapes().get(id);float nearest=Float.POSITIVE_INFINITY;
            for(int i=0;i<boxes.length;i+=6) {
                if(axis!=0&&(x-cx<boxes[i]||x-cx>boxes[i+3])) continue;
                if(axis!=1&&(y-cy<boxes[i+1]||y-cy>boxes[i+4])) continue;
                if(axis!=2&&(z-cz<boxes[i+2]||z-cz>boxes[i+5])) continue;
                float origin=axis==0?x:axis==1?y:z, cell=axis==0?cx:axis==1?cy:cz;
                float distance=(cell+boxes[i+axis+(sign>0?0:3)]-origin)*sign;
                if(distance>.001f&&distance<=range) nearest=Math.min(nearest,distance);
            }
            if(Float.isFinite(nearest)) return nearest;
        }
        return Float.POSITIVE_INFINITY;
    }
}
