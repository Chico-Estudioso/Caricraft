#!/usr/bin/env python3
"""
Prepara un servidor de prueba con los mods de servidor de un .mrpack de COBBLEVERSE y el JAR de
Caricraft Mod, para comprobar la compatibilidad real antes de actualizar versiones.

Uso:
    python3 tools/cobbleverse_server_test.py "COBBLEVERSE 1.8.0.mrpack" /ruta/servidor build/libs/caricraft-0.1.0.jar

Después:
    cd /ruta/servidor && java -Xmx6G -jar fabric-server-launch.jar nogui

Notas:
- Descarga cada mod desde la URL del índice del modpack y verifica su SHA-512. No redistribuye nada.
- Incluye 'athena' y 'fusion' aunque el índice los marque solo cliente: CobbleFurnies y Rechiseled
  los necesitan también en el servidor.
- COBBLEVERSE necesita Java 21 (Cobblemon exige exactamente 21).
"""
import hashlib
import json
import os
import sys
import urllib.request
import zipfile

SERVER_LIBRARIES = ("athena-", "fusion-")


def main(mrpack, server_dir, caricraft_jar):
    os.makedirs(os.path.join(server_dir, "mods"), exist_ok=True)
    with zipfile.ZipFile(mrpack) as pack:
        index = json.loads(pack.read("modrinth.index.json"))
        deps = index["dependencies"]
        for name in pack.namelist():
            if name.startswith(("overrides/config/", "overrides/datapacks/")) and not name.endswith("/"):
                dest = os.path.join(server_dir, name[len("overrides/"):])
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "wb") as out:
                    out.write(pack.read(name))

    for entry in index["files"]:
        name = os.path.basename(entry["path"])
        server = entry.get("env", {}).get("server", "required")
        if not entry["path"].startswith("mods/"):
            continue
        if server == "unsupported" and not name.startswith(SERVER_LIBRARIES):
            continue
        dest = os.path.join(server_dir, "mods", name)
        data = urllib.request.urlopen(entry["downloads"][0], timeout=120).read()
        if hashlib.sha512(data).hexdigest() != entry["hashes"]["sha512"]:
            sys.exit(f"Hash incorrecto: {name}")
        with open(dest, "wb") as out:
            out.write(data)
        print("ok", name)

    with open(caricraft_jar, "rb") as src, open(os.path.join(server_dir, "mods", os.path.basename(caricraft_jar)), "wb") as out:
        out.write(src.read())

    installer = json.load(urllib.request.urlopen("https://meta.fabricmc.net/v2/versions/installer"))[0]["version"]
    url = (f"https://meta.fabricmc.net/v2/versions/loader/{deps['minecraft']}/{deps['fabric-loader']}"
           f"/{installer}/server/jar")
    with open(os.path.join(server_dir, "fabric-server-launch.jar"), "wb") as out:
        out.write(urllib.request.urlopen(url, timeout=120).read())
    with open(os.path.join(server_dir, "eula.txt"), "w") as out:
        out.write("eula=true\n")
    print(f"Listo: Minecraft {deps['minecraft']}, Fabric Loader {deps['fabric-loader']}.")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
