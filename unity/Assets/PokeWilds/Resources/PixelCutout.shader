Shader "PokeWilds/PixelCutout"
{
    Properties { _MainTex ("Source pixels", 2D) = "white" {} }
    SubShader
    {
        Tags { "Queue"="AlphaTest" "RenderType"="TransparentCutout" }
        Cull Off ZWrite On
        Pass
        {
            CGPROGRAM
            #pragma vertex vert
            #pragma fragment frag
            #pragma multi_compile_fog
            #include "UnityCG.cginc"
            sampler2D _MainTex;
            struct Input { float4 vertex:POSITION; float2 uv:TEXCOORD0; fixed4 color:COLOR; };
            struct Output { float4 vertex:SV_POSITION; float2 uv:TEXCOORD0; fixed4 color:COLOR; UNITY_FOG_COORDS(1) };
            Output vert(Input v) { Output o; o.vertex=UnityObjectToClipPos(v.vertex); o.uv=v.uv; o.color=v.color; UNITY_TRANSFER_FOG(o,o.vertex); return o; }
            fixed4 frag(Output i):SV_Target { fixed4 c=tex2D(_MainTex,i.uv)*i.color; clip(c.a-.1); UNITY_APPLY_FOG(i.fogCoord,c); return c; }
            ENDCG
        }
    }
}
