"""Run the real GitHub routing code against deterministic HTTP connections, without Android."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

class GitHubDownloadsTest(unittest.TestCase):
    def test_routing_headers_and_fallback(self):
        java_home = Path(os.environ.get("JAVA_HOME", "D:/Java/jdk-17"))
        javac = shutil.which("javac") or str(java_home / "bin/javac.exe")
        java = shutil.which("java") or str(java_home / "bin/java.exe")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            files = {
                "android/content/Context.java": "package android.content; public abstract class Context { public static final int MODE_PRIVATE=0; public abstract SharedPreferences getSharedPreferences(String n,int m); }",
                "android/content/SharedPreferences.java": "package android.content; public interface SharedPreferences { String getString(String k,String d); Editor edit(); interface Editor { Editor putString(String k,String v); void apply(); } }",
                "Check.java": r'''import com.droiddeck.launcher.core.GitHubDownloads;
import java.net.*;
import java.io.*;
import java.util.*;
public class Check {
 static int mode; static List<Fake> opened=new ArrayList<>();
 static class Fake extends HttpURLConnection {
  boolean closed;
  Fake(URL u){super(u);opened.add(this);}
  public void connect(){} public void disconnect(){closed=true;} public boolean usingProxy(){return false;}
  public int getResponseCode() throws IOException {
   if(url.getHost().equals("mirror.example") && mode==1) throw new IOException("offline");
   return url.getHost().equals("mirror.example") && mode==2 ? 503 : 206;
  }
  public String getContentType(){return mode==3 && url.getHost().equals("mirror.example") ? "text/html" : "application/octet-stream";}
 }
 static void require(boolean b){if(!b)throw new AssertionError();}
 static void prefix(String value)throws Exception{
  var field=GitHubDownloads.class.getDeclaredField("prefix");field.setAccessible(true);field.set(null,value);
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Fake(u);}} : null);
  String asset="https://github.com/Droid-Deck/DroidDeck/releases/download/v1/test.apk";
  prefix("https://mirror.example/");
  require(GitHubDownloads.accelerated(asset).equals("https://mirror.example/"+asset));
  String raw="https://raw.githubusercontent.com/owner/repo/main/file.json";
  require(GitHubDownloads.accelerated(raw).endsWith(raw));
  for(String url:List.of("https://api.github.com/repos/a/b/releases", "https://github.com.evil/a/b/releases/download/v1/a", "https://github.com/a/b/issues/1", "http://github.com/a/b/releases/download/v1/a", "https://user:pass@github.com/a/b/releases/download/v1/a", "https://github.com:8443/a/b/releases/download/v1/a", "https://store.steampowered.com/a"))require(GitHubDownloads.accelerated(url).equals(url));
  require(GitHubDownloads.normalize(" https://mirror.example ").equals("https://mirror.example/"));
  for(String invalid:List.of("http://mirror.example", "https://u:p@mirror.example", "https://mirror.example/?token=a", "https://mirror.example/#a", "garbage")){
   boolean rejected=false;try{GitHubDownloads.normalize(invalid);}catch(IllegalArgumentException e){rejected=true;}require(rejected);
  }
  for(mode=0;mode<4;mode++){
   opened.clear();var c=GitHubDownloads.open(asset,x->{x.setRequestProperty("Range","bytes=123-");x.setConnectTimeout(30000);});
   require(c.getRequestProperty("Range").equals("bytes=123-"));
   require(opened.size()==(mode==0?1:2));
   require(c.getURL().getHost().equals(mode==0?"mirror.example":"github.com"));
   if(mode!=0)require(opened.get(0).closed);
   c.disconnect();
  }
  prefix("");require(GitHubDownloads.accelerated(asset).equals(asset));
  System.out.println("Routing, disabled mode, validation, Range headers, network/HTTP/HTML fallback passed");
 }
}''',
            }
            for name, content in files.items():
                dest = root / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_text(content, encoding="utf-8")
            subprocess.run([javac, "-encoding", "UTF-8", "-d", str(root),
                            str(ROOT / "app/src/main/java/com/droiddeck/launcher/core/GitHubDownloads.java"),
                            *[str(root / name) for name in files]], check=True)
            subprocess.run([java, "-cp", str(root), "Check"], check=True)
