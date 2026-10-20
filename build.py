"""Build with installed JDK + Android SDK only; no Gradle or network downloads.

python build.py --sdk PATH --jdk PATH --keystore PATH --out PATH
The keystore is local to this custom app. Preserve it for future updates.
"""
import argparse
import os
import pathlib
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--sdk', required=True)
p.add_argument('--jdk', required=True)
p.add_argument('--keystore', required=True)
p.add_argument('--out', required=True)
p.add_argument('--work', required=True)
a = p.parse_args()
root = pathlib.Path(__file__).resolve().parent
sdk, jdk, work = pathlib.Path(a.sdk), pathlib.Path(a.jdk), pathlib.Path(a.work)
tools = sdk/'build-tools'/'35.0.0'
android = sdk/'platforms'/'android-35'/'android.jar'
for folder in ['generated', 'classes', 'dex']: (work/folder).mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(v) for v in args], check=True)

run(tools/'aapt2.exe', 'compile', '--dir', root/'res', '-o', work/'resources.zip')
run(tools/'aapt2.exe', 'link', '-I', android, '--manifest', root/'AndroidManifest.xml',
    '--java', work/'generated', '-o', work/'resources.apk', work/'resources.zip')
sources = list((root/'src').rglob('*.java')) + list((work/'generated').rglob('*.java'))
run(jdk/'bin/javac.exe', '--release', '8', '-encoding', 'UTF-8', '-classpath', android,
    '-d', work/'classes', *sources)
with zipfile.ZipFile(work/'classes.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for file in (work/'classes').rglob('*.class'): z.write(file, file.relative_to(work/'classes'))
run(jdk/'bin/java.exe', '-cp', tools/'lib/d8.jar', 'com.android.tools.r8.D8',
    '--min-api', '26', '--lib', android, '--output', work/'dex', work/'classes.jar')
with zipfile.ZipFile(work/'resources.apk') as r, zipfile.ZipFile(work/'unsigned.apk', 'w') as z:
    for item in r.infolist(): z.writestr(item, r.read(item.filename))
    z.write(work/'dex/classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
run(tools/'zipalign.exe', '-f', '4', work/'unsigned.apk', work/'aligned.apk')
keystore = pathlib.Path(a.keystore)
if not keystore.exists():
    keystore.parent.mkdir(parents=True, exist_ok=True)
    run(jdk/'bin/keytool.exe', '-genkeypair', '-keystore', keystore, '-storepass', 'local-launcher',
        '-keypass', 'local-launcher', '-alias', 'arabic-tv', '-dname', 'CN=Arabic TV Local Build',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
pathlib.Path(a.out).parent.mkdir(parents=True, exist_ok=True)
run(jdk/'bin/java.exe', '-jar', tools/'lib/apksigner.jar', 'sign', '--ks', keystore,
    '--ks-key-alias', 'arabic-tv', '--ks-pass', 'pass:local-launcher', '--out', a.out, work/'aligned.apk')
run(jdk/'bin/java.exe', '-jar', tools/'lib/apksigner.jar', 'verify', '--verbose', a.out)
print('APK:', a.out, 'size:', pathlib.Path(a.out).stat().st_size, 'bytes')
