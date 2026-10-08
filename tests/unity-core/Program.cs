using System;
using PokeWilds.Core;

internal static class Program
{
    private static int checks;
    private static void Check(bool ok,string label){checks++;if(!ok)throw new Exception(label);}
    private static void Near(float a,float b,string label){Check(Math.Abs(a-b)<.00001f,label);}
    private static void Reject(Action action){bool rejected=false;try{action();}catch(ArgumentException){rejected=true;}Check(rejected,"invalid input accepted");}
    public static void Main()
    {
        foreach(int size in new[]{16,24,32,48,64,96,128,256})
        {
            var s=new PixelSampling(size*3,size*2,size,size);
            for(int y=-50;y<=50;y++)for(int x=-50;x<=50;x++)
            {
                int expected=(((-y)%2+2)%2)*3+((x%3+3)%3);
                Check(s.Index(x*16,y*16)==expected,"negative wrapping");
            }
            for(int i=0;i<s.Count;i++)Check(s.SourceX(i)+size<=size*3&&s.SourceY(i)+size<=size*2,"source bounds");
        }
        var sample=new PixelSampling(64,64,32,32);
        Check(sample.Index(-.01,.01)==3,"fractional negative boundary");
        Reject(()=>new PixelSampling(48,48,32,32));Reject(()=>new PixelSampling(0,16,16,16));
        Reject(()=>new PixelSampling(8192,8192,1,1));Reject(()=>sample.Index(double.NaN,0));
        var time=new FrameTimeline(new[]{10,20,30},8);
        Check(time.Index(Facing.NorthEast,0)==9,"PMD NE row");Check(time.Index(Facing.SouthWest,0)==21,"PMD SW row");
        Check(time.Index(Facing.NorthEast,.2)==10,"timed frame");Check(time.Index(Facing.NorthEast,1.01)==9,"timed wrap");
        Check(new FrameTimeline(new[]{10,20},1).Index(Facing.North,0)==0,"one-direction clips");
        Reject(()=>new FrameTimeline(new[]{0},8));Reject(()=>new FrameTimeline(new[]{10},4));
        var c=new SurfaceCell{X=-1,Z=-1,H00=0,H10=2,H01=4,H11=7};
        Near(c.Height(-.5f,-.5f),3.5f,"diagonal agrees");Near(c.Height(-.25f,-.5f),4,"SE triangle");
        Near(c.Height(-.5f,-.25f),4.5f,"NW triangle");
        var grid=MigrationFixture.Create();Check(grid.Count==1024,"fixture count");
        Check(grid.CanMove(.5f,.5f,.5f,5.5f),"continuous ramp climb");
        Check(grid.CanMove(.5f,5.5f,.5f,.5f),"continuous ramp descent");
        Check(!grid.CanMove(3.5f,3.5f,3.5f,4.5f),"cliff cannot be crossed");
        Check(!grid.CanMove(10.5f,.5f,12.5f,.5f),"water blocked");
        Check(!grid.CanMove(.5f,.5f,100,.5f),"out-of-world blocked");
        var flat=new SurfaceGrid();for(int x=0;x<5;x++)for(int z=0;z<3;z++)flat.Add(new SurfaceCell{X=x,Z=z,Blocked=x==2});
        Check(!flat.CanMove(.5f,1.5f,4.5f,1.5f),"swept collision prevents tunneling");
        Check(!flat.CanMove(float.NaN,0,0,0),"NaN movement");
        var same=MigrationFixture.Create();foreach(SurfaceCell t in grid.Cells)
        {
            var other=same.Cell(t.X,t.Z);Check(t.Ground==other.Ground&&t.Prop==other.Prop&&t.H11==other.H11,"deterministic fixture");
        }
        Console.WriteLine("UNITY CORE PASS: "+checks+" checks (no Unity Editor or rendering executed)");
    }
}
