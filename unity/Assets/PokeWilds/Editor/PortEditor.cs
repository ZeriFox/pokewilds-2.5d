using System;
using System.Diagnostics;
using System.IO;
using PokeWilds.Core;
using PokeWilds.UnityPort;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace PokeWilds.EditorPort
{
    public static class PortEditor
    {
        private const string ScenePath="Assets/PokeWilds/Scenes/Migration.unity";
        [MenuItem("Tools/PokeWilds/Import Java assets")]
        public static void ImportAssets()
        {
            string root=Directory.GetParent(Application.dataPath).Parent.FullName;
            string script=Path.Combine(root,"tools","prepare_unity_assets.py");
            if(!File.Exists(script))throw new InvalidOperationException("Apri unity/ all'interno del clone completo del repository.");
            string executable=Environment.GetEnvironmentVariable("POKEWILDS_PYTHON");
            if(string.IsNullOrWhiteSpace(executable))executable=Application.platform==RuntimePlatform.WindowsEditor?"python":"python3";
            AssetDatabase.DisallowAutoRefresh();
            try
            {
                var info=new ProcessStartInfo(executable,"\""+script+"\" --root \""+root+"\"")
                    {UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true,RedirectStandardError=true,WorkingDirectory=root};
                using(var process=new Process { StartInfo=info })
                {
                    process.Start();
                    // Drain both streams concurrently: large error output must not deadlock the Editor.
                    var stdout=process.StandardOutput.ReadToEndAsync();var stderr=process.StandardError.ReadToEndAsync();
                    if(!process.WaitForExit(180000)){process.Kill();throw new TimeoutException("Import timed out; previous bridge output was not accepted as verified.");}
                    string output=stdout.GetAwaiter().GetResult(),errors=stderr.GetAwaiter().GetResult();
                    if(process.ExitCode!=0)throw new InvalidOperationException(errors+"\n"+output+"\nPrerequisito: python -m pip install Pillow==11.3.0");
                    UnityEngine.Debug.Log(output);
                }
            }
            finally {AssetDatabase.AllowAutoRefresh();AssetDatabase.Refresh(ImportAssetOptions.ForceSynchronousImport);}
            ValidateImportedData();
            EditorSceneManager.OpenScene(ScenePath);
        }
        [MenuItem("Tools/PokeWilds/Validate imported data")]
        public static void ValidateImportedData()
        {
            using(var assets=new PortAssets())
            {
                foreach(RegionData r in assets.Catalog.regions)assets.UV(r.frame);
                foreach(PokemonData p in assets.Catalog.pokemon)foreach(ClipData c in p.clips)foreach(PixelFrame f in c.frames)assets.UV(f);
                if(MigrationFixture.Create().Count!=1024)throw new InvalidOperationException("Fixture grid changed unexpectedly.");
                UnityEngine.Debug.Log("Unity Editor data validation PASS. This is not a complete Play Mode/gameplay validation.");
            }
        }
        [MenuItem("Tools/PokeWilds/Open migration scene")]
        public static void OpenScene(){EditorSceneManager.OpenScene(ScenePath);}
        [MenuItem("Tools/PokeWilds/Build Windows M0 prototype")]
        public static void BuildWindows()
        {
            ValidateImportedData();
            if(!BuildPipeline.IsBuildTargetSupported(BuildTargetGroup.Standalone,BuildTarget.StandaloneWindows64))
                throw new InvalidOperationException("Installa Windows Build Support per questo Editor.");
            string target=Environment.GetEnvironmentVariable("POKEWILDS_UNITY_BUILD");
            if(string.IsNullOrEmpty(target))target="Builds/Windows-M0/PokeWilds-Unity-M0.exe";
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(target)));
            BuildReport report=BuildPipeline.BuildPlayer(new BuildPlayerOptions
                { scenes=new[]{ScenePath},locationPathName=target,target=BuildTarget.StandaloneWindows64,options=BuildOptions.Development });
            if(report.summary.result!=BuildResult.Succeeded)throw new InvalidOperationException("Unity build failed: "+report.summary.result);
            UnityEngine.Debug.Log("M0 prototype build: "+Path.GetFullPath(target));
        }
    }
    public sealed class PortTextureImporter : AssetPostprocessor
    {
        private void OnPreprocessTexture()
        {
            if(!assetPath.StartsWith("Assets/PokeWilds/Resources/Imported/",StringComparison.Ordinal))return;
            var texture=(TextureImporter)assetImporter;
            texture.textureType=TextureImporterType.Default;texture.filterMode=FilterMode.Point;
            texture.mipmapEnabled=false;texture.npotScale=TextureImporterNPOTScale.None;
            texture.wrapMode=TextureWrapMode.Clamp;texture.textureCompression=TextureImporterCompression.Uncompressed;
            texture.alphaSource=TextureImporterAlphaSource.FromInput;texture.alphaIsTransparency=true;
            texture.maxTextureSize=8192;texture.sRGBTexture=true;texture.isReadable=false;
        }
    }
}
