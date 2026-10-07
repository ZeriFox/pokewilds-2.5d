package com.pkmngen.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** A derived surface constrained by saved ledges. Never writes tiles or saves. */
public final class WorldElevation {
   private static final int[][] DIRS={{0,-1},{0,1},{-1,0},{1,0}};
   private static final int[] OPPOSITE={1,0,3,2};
   private final Map<Long,Cell> cells=new HashMap<>();
   private Map<Vector2,Tile> source;
   private int sourceSize,rebuilds,conflicts;
   private boolean dirty;

   public void update(Map<Vector2,Tile> tiles) {
      if(source==tiles && sourceSize==tiles.size() && !dirty)return;
      source=tiles;sourceSize=tiles.size();dirty=false;cells.clear();conflicts=0;rebuilds++;
      ArrayList<Cell> ordered=new ArrayList<>();
      for(Tile tile:tiles.values()){Cell cell=new Cell(tile);cells.put(key(tile.position.x,tile.position.y),cell);ordered.add(cell);}
      ordered.sort((a,b)->a.y==b.y?Integer.compare(a.x,b.x):Integer.compare(a.y,b.y));
      ArrayDeque<Cell> queue=new ArrayDeque<>();
      for(int i=0;i<ordered.size();i++) {
         Cell cell=ordered.get(i);cell.id=i;cell.parent=cell;
         for(int d=0;d<4;d++){int[] delta=DIRS[d];cell.next[d]=at(cell.x+delta[0],cell.y+delta[1]);cell.exterior|=cell.next[d]==null;}
         if(cell.edges!=0 || cell.ramp){cell.distance=0;queue.addLast(cell);}
      }
      for(Cell cell:ordered)if(Integer.bitCount(cell.edges)==2) {
         int a=Integer.numberOfTrailingZeros(cell.edges),b=Integer.numberOfTrailingZeros(cell.edges&~(1<<a));
         Cell sideA=cell.next[a],sideB=cell.next[b];
         if(cell.tile.nameUpper.contains("_inner") || sideA!=null && sideB!=null
            && (sideA.rawEdges&(1<<b))!=0 && (sideB.rawEdges&(1<<a))!=0) {
            cell.diagonal=at(cell.x+DIRS[a][0]+DIRS[b][0],cell.y+DIRS[a][1]+DIRS[b][1]);cell.edges=0;
         }
      }
      for(Cell cell:ordered)if(!cell.ramp && cell.rawEdges==0 && !cell.tile.isSolid && !cell.tile.isWater && !cell.tile.isLava) {
         int direction=openingDirection(cell);
         if(direction>=0){cell.ramp=true;cell.direction=direction;cell.distance=0;queue.addLast(cell);}
      }
      for(Cell cell:ordered)if(cell.ramp && cell.direction<0)cell.direction=rampDirection(cell);
      while(!queue.isEmpty()) {
         Cell cell=queue.removeFirst();
         for(Cell next:cell.next)if(next!=null && next.distance>cell.distance+1){next.distance=cell.distance+1;queue.addLast(next);}
      }
      ArrayList<Constraint> constraints=new ArrayList<>();
      for(Cell cell:ordered)if(cell.diagonal!=null)constraints.add(new Constraint(cell,cell.diagonal,-cell.rise,1000));
      for(Cell cell:ordered)for(int d:new int[]{1,3}) {
         Cell next=cell.next[d];if(next==null)continue;
         float rise=0;int priority=0;
         boolean first=(cell.edges&(1<<d))!=0,second=(next.edges&(1<<OPPOSITE[d]))!=0;
         if(first || second) {
            // A saved face belongs to its high cell; the adjacent cell is low.
            rise=first?-cell.rise:next.rise;priority=1000;
            if(first && second)conflicts++;
         } else {
            float slopeA=rampStep(cell,d),slopeB=rampStep(next,d);
            if(slopeA!=0 || slopeB!=0){rise=(slopeA+slopeB)*.5f;priority=900;}
            else { if(rampSide(cell,next,d))continue; priority=1+Math.min(100,Math.min(cell.distance,next.distance)); }
         }
         constraints.add(new Constraint(cell,next,rise,priority));
      }
      constraints.sort((a,b)->a.priority!=b.priority?Integer.compare(b.priority,a.priority)
         :a.a.id!=b.a.id?Integer.compare(a.a.id,b.a.id):Integer.compare(a.b.id,b.b.id));
      // Strong local evidence is solved before flat continuation. An open contour
      // therefore cannot force both sides of every cliff into one flat component.
      // Rejected flat continuations become shared-corner slopes below, not barriers.
      for(Constraint constraint:constraints)if(!join(constraint.a,constraint.b,constraint.rise))conflicts++;
      Map<Cell,Map<Float,Integer>> datums=new HashMap<>();
      for(Cell cell:ordered){Cell root=find(cell);if(cell.exterior)datums.computeIfAbsent(root,k->new HashMap<>()).merge(cell.offset,1,Integer::sum);}
      Map<Cell,Float> offsets=new HashMap<>();
      for(Cell cell:ordered) {
         Cell root=find(cell);
         if(!offsets.containsKey(root)) {
            float datum=cell.offset;int support=-1;Map<Float,Integer> choices=datums.get(root);
            if(choices!=null)for(Map.Entry<Float,Integer> candidate:choices.entrySet()) {
               if(candidate.getValue()>support || candidate.getValue()==support && candidate.getKey()<datum) {
                  support=candidate.getValue();datum=candidate.getKey();
               }
            }
            offsets.put(root,datum);
         }
         cell.height=cell.offset-offsets.get(root);
      }
      // Every non-cliff vertex is shared. This turns implicit openings into a
      // continuous ramp and prevents height jumps or cracks on walkable ground.
      for(Cell cell:ordered)for(int corner=0;corner<4;corner++)cell.corner[corner]=corner(cell,corner&1,corner>>1);
   }
   private Cell find(Cell cell) {
      if(cell.parent!=cell){Cell parent=cell.parent;cell.parent=find(parent);cell.offset+=parent.offset;}
      return cell.parent;
   }
   private boolean join(Cell a,Cell b,float rise) {
      Cell rootA=find(a),rootB=find(b);float difference=rise+a.offset-b.offset;
      if(rootA==rootB)return Math.abs(b.offset-a.offset-rise)<.01f;
      if(rootA.size<rootB.size){rootA.parent=rootB;rootA.offset=-difference;rootB.size+=rootA.size;}
      else {rootB.parent=rootA;rootB.offset=difference;rootA.size+=rootB.size;}
      return true;
   }
   /** A one-cell breach between two adjacent, agreeing contour ends is a
    * local ramp. This keeps a deleted edge from turning a plateau margin into
    * a half-height gutter; no Tile or original walkability is changed. */
   private int openingDirection(Cell cell) {
      int selected=-1;
      for(int direction=0;direction<4;direction++) {
         int tx=-DIRS[direction][1],ty=DIRS[direction][0];
         Cell a=at(cell.x+tx,cell.y+ty),b=at(cell.x-tx,cell.y-ty);
         if(a==null || b==null || (a.rawEdges&(1<<direction))==0 || (b.rawEdges&(1<<direction))==0)continue;
         if(selected>=0)return -1; // Conflicting corner cues are not enough evidence.
         selected=direction;
      }
      return selected;
   }
   private int rampDirection(Cell cell) {
      for(int distance=1;distance<=8;distance++) {
         int[] votes=new int[4];
         for(int side=0;side<4;side++) {
            Cell nearby=at(cell.x+DIRS[side][0]*distance,cell.y+DIRS[side][1]*distance);
            if(nearby==null || nearby.ramp)continue;
            for(int d=0;d<4;d++)if((nearby.edges&(1<<d))!=0 && DIRS[d][0]*DIRS[side][0]+DIRS[d][1]*DIRS[side][1]==0)votes[d]++;
         }
         int selected=-1,support=0;boolean tied=false;
         for(int d=0;d<4;d++)if(votes[d]>support){selected=d;support=votes[d];tied=false;}else if(votes[d]>0 && votes[d]==support)tied=true;
         if(selected>=0 && !tied)return selected;
      }
      return -1;
   }
   private float rampStep(Cell cell,int direction) {
      if(!cell.ramp || cell.direction<0)return 0;
      if(direction==cell.direction)return -cell.rise;
      return direction==OPPOSITE[cell.direction]?cell.rise:0;
   }
   private boolean rampSide(Cell a,Cell b,int direction) {
      return a.ramp!=b.ramp && ((a.ramp && a.direction>=0 && direction!=a.direction && direction!=OPPOSITE[a.direction])
         || (b.ramp && b.direction>=0 && direction!=b.direction && direction!=OPPOSITE[b.direction]));
   }
   private boolean hardEdge(Cell a,Cell b,int direction) {
      return rampSide(a,b,direction) && (a.rawEdges!=0 || b.rawEdges!=0 || a.tile.isSolid || b.tile.isSolid) || (a.edges&(1<<direction))!=0 || (b.edges&(1<<OPPOSITE[direction]))!=0;
   }
   private float corner(Cell cell,int dx,int dy) {
      int vx=cell.x+dx,vy=cell.y+dy;
      Cell[] group={at(vx-1,vy-1),at(vx,vy-1),at(vx-1,vy),at(vx,vy)};
      boolean[] seen=new boolean[4];int own=(1-dy)*2+1-dx;seen[own]=true;
      for(int pass=0;pass<3;pass++)for(int i=0;i<4;i++)if(seen[i] && group[i]!=null) {
         int horizontal=i^1,vertical=i^2;
         if(group[horizontal]!=null && !hardEdge(group[i],group[horizontal],i%2==0?3:2))seen[horizontal]=true;
         if(group[vertical]!=null && !hardEdge(group[i],group[vertical],i<2?1:0))seen[vertical]=true;
      }
      float total=0;int count=0;
      for(int i=0;i<4;i++)if(seen[i] && group[i]!=null) {
         Cell neighbor=group[i];float value=neighbor.height;
         if(neighbor.ramp && neighbor.direction>=0) {
            float fx=vx-neighbor.x-.5f,fy=vy-neighbor.y-.5f;
            value-=neighbor.rise*(DIRS[neighbor.direction][0]*fx+DIRS[neighbor.direction][1]*fy);
         }
         total+=value;count++;
      }
      return count==0?cell.height:total/count;
   }
   public void observe(Tile tile) {
      Cell cell=cells.get(key(tile.position.x,tile.position.y));
      if(cell==null || cell.tile!=tile || cell.signature!=signature(tile))dirty=true;
   }
   public float height(float x,float y) {
      Cell cell=cells.get(key(x,y));if(cell==null)return 0;
      float fx=MathUtils.clamp((x-cell.x*16)/16,0,1),fy=MathUtils.clamp((y-cell.y*16)/16,0,1);
      // Same 0->3 diagonal as the renderer's two surface triangles.
      return fx>=fy ? cell.corner[0]+fx*(cell.corner[1]-cell.corner[0])+fy*(cell.corner[3]-cell.corner[1])
         : cell.corner[0]+fy*(cell.corner[2]-cell.corner[0])+fx*(cell.corner[3]-cell.corner[2]);
   }
   public float tileHeight(Tile tile){return height(tile.position.x+8,tile.position.y+8);}
   public float lowHeight(Tile tile) {
      Cell cell=cells.get(key(tile.position.x,tile.position.y));if(cell==null)return 0;
      float result=tileHeight(tile);
      for(int d=0;d<4;d++)if((cell.edges&(1<<d))!=0 && cell.next[d]!=null)result=Math.min(result,cell.next[d].height);
      for(float value:cell.corner)result=Math.min(result,value);
      return result;
   }
   public boolean raised(Tile tile){return Math.abs(tileHeight(tile)-lowHeight(tile))>.1f;}
   public boolean isSloped(Tile tile) {
      Cell cell=cells.get(key(tile.position.x,tile.position.y));if(cell==null)return false;
      for(int i=1;i<4;i++)if(Math.abs(cell.corner[i]-cell.corner[0])>.1f)return true;
      return false;
   }
   public int getRebuilds(){return rebuilds;}
   public int getConflicts(){return conflicts;}
   private Cell at(int x,int y){return cells.get(((long)x<<32)^(y&0xffffffffL));}
   private static long key(float x,float y){return((long)MathUtils.floor(x/16)<<32)^(MathUtils.floor(y/16)&0xffffffffL);}
   private static int signature(Tile tile) {
      boolean ramp=ModernWorldGenerator.isRamp(tile);
      float rise=BiomeProfiles.forTile(tile).number("height",ramp?"rampRise":"cliffHeight",8);
      // Texture names, time-of-day and tidalwater/tidaloff do not alter the surface.
      // Rebuild only when a real constraint or a ramp-side solid boundary changes.
      return java.util.Objects.hash(edges(tile),tile.nameUpper!=null && tile.nameUpper.contains("_inner"),ramp,rise,tile.isSolid,tile.isWater,tile.isLava);
   }
   private static int edges(Tile tile) {
      String name=tile.nameUpper==null?"":tile.nameUpper;
      if(!name.contains("ledges3"))return 0;
      String[] parts=name.split("_");if(parts.length<2)return 0;
      int result=0;
      for(char c:parts[1].toCharArray())result|=c=='N'?1:c=='S'?2:c=='E'?4:c=='W'?8:0;
      return result;
   }
   private static final class Constraint {
      final Cell a,b;final float rise;final int priority;
      Constraint(Cell a,Cell b,float rise,int priority){this.a=a;this.b=b;this.rise=rise;this.priority=priority;}
   }
   private static final class Cell {
      final Tile tile;final int x,y,signature,rawEdges;boolean ramp;final float rise;
      final Cell[] next=new Cell[4];final float[] corner=new float[4];
      Cell parent,diagonal;int edges,id,size=1,distance=1000000,direction=-1;float offset,height;boolean exterior;
      Cell(Tile tile){this.tile=tile;x=MathUtils.floor(tile.position.x/16);y=MathUtils.floor(tile.position.y/16);signature=signature(tile);
         ramp=ModernWorldGenerator.isRamp(tile);edges=edges(tile);rawEdges=edges;
         rise=BiomeProfiles.forTile(tile).number("height",ramp?"rampRise":"cliffHeight",8);}
   }
}
