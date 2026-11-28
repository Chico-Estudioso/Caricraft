#!/usr/bin/env python3
"""
QA con Cobblemon real: monta un servidor y un cliente de producción mínimos (Cobblemon, CobbleDollars, Fabric API,
Kotlin, Architectury y Caricraft) y juega un guion de QaAutomation en el cliente. En el entorno de desarrollo no se
puede cargar Cobblemon, así que esta es la forma de ver los modelos 3D y probar las compras de verdad.

Requisitos:
- Un servidor COBBLEVERSE preparado con tools/cobbleverse_server_test.py (de ahí salen los mods y las librerías).
- `./gradlew build remapQaJar` (JAR del mod y JAR de QA remapeados).
- La caché de Loom con el cliente 1.21.1 (cualquier `./gradlew runClient` previo la deja) y, sin pantalla, xvfb-run.

Uso:
    python3 tools/cobblemon_qa.py /ruta/servidor_cobbleverse /ruta/trabajo W

El rol W recorre el Mercado (pestaña Cobbleverse) y el CobbleCentro: regalo, compra, venta, modelos 3D e
intercambio prodigioso. El rol D prueba el bloque del dúo (Centro Pokémon de bolsillo, caza del día, Pokédex del dúo,
expediciones, brújula de variocolor) y, con RCT, la opción de apariciones según el level cap. Capturas en <trabajo>/client/screenshots y registros en <trabajo>/logs.
No redistribuye nada: copia los JAR del servidor que ya tienes.
"""
import glob
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import time
import uuid

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODS = ("Cobblemon-fabric-", "fabric-api-", "fabric-language-kotlin-", "CobbleDollars-fabric-", "architectury-")
# Mods extra por rol: el D prueba también el level cap de los entrenadores (RCT y sus dependencias).
ROLE_MODS = {"D": ("rctmod-", "rctapi-", "ForgeConfigAPIPort-")}
GRADLE = os.path.expanduser("~/.gradle/caches")
MC = "1.21.1"
PORT = 25566


def offline_uuid(name):
    h = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    h[6] = (h[6] & 0x0F) | 0x30
    h[8] = (h[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def copy_mods(source, target, extra, prefixes=MODS):
    os.makedirs(target, exist_ok=True)
    for name in os.listdir(target):
        if name.endswith(".jar"):
            os.remove(os.path.join(target, name))
    for name in sorted(os.listdir(os.path.join(source, "mods"))):
        if name.startswith(prefixes):
            shutil.copy(os.path.join(source, "mods", name), target)
    for path in extra:
        shutil.copy(path, target)


def allowed(library):
    """Reglas de Mojang para Linux."""
    result = not library.get("rules")
    for rule in library.get("rules", []):
        os_name = rule.get("os", {}).get("name")
        if os_name is None or os_name == "linux":
            result = rule["action"] == "allow"
    return result


def gradle_jar(group, name, version, classifier=None):
    file = f"{name}-{version}{'-' + classifier if classifier else ''}.jar"
    hits = glob.glob(os.path.join(GRADLE, "modules-2/files-2.1", group, name, version, "*", file))
    return hits[0] if hits else None


def asset_index():
    """Loom guarda el índice como «1.21.1-17.json»; el cliente lo pide por ese nombre (sin él no hay idiomas)."""
    names = sorted(os.listdir(os.path.join(GRADLE, "fabric-loom/assets/indexes")))
    return next((n[:-5] for n in names if n.startswith(MC)), names[0][:-5])


def client_classpath(server_source):
    info = json.load(open(os.path.join(GRADLE, "fabric-loom", MC, "mojang_minecraft_info.json")))
    jars = []
    for library in info["libraries"]:
        if not allowed(library):
            continue
        group, name, version, *rest = library["name"].split(":")
        if rest and ("aarch" in rest[0]) != (platform.machine() in ("aarch64", "arm64")):
            continue
        jar = gradle_jar(group, name, version, rest[0] if rest else None)
        if jar is None:
            print("falta en la caché de Gradle:", library["name"], file=sys.stderr)
            continue
        jars.append(jar)
    libraries = os.path.join(server_source, "libraries")
    for pattern in ("net/fabricmc/fabric-loader/*/*.jar", "net/fabricmc/intermediary/*/*.jar",
                    "net/fabricmc/sponge-mixin/*/*.jar", "org/ow2/asm/*/*/*.jar"):
        jars += sorted(glob.glob(os.path.join(libraries, pattern)))
    jars.append(os.path.join(GRADLE, "fabric-loom", MC, "minecraft-client.jar"))
    return ":".join(dict.fromkeys(jars))


def prepare(server_source, work, role=""):
    prefixes = MODS + ROLE_MODS.get(role, ())
    mod_jar = os.path.join(REPO, "build/libs/caricraft-0.1.0.jar")
    qa_jar = os.path.join(REPO, "build/qa/caricraft-0.1.0-qa.jar")
    for jar in (mod_jar, qa_jar):
        if not os.path.exists(jar):
            sys.exit(f"falta {jar}: ejecuta ./gradlew build remapQaJar")
    server = os.path.join(work, "server")
    os.makedirs(server, exist_ok=True)
    for link in ("libraries", "versions"):
        path = os.path.join(server, link)
        if not os.path.exists(path):
            os.symlink(os.path.join(server_source, link), path)
    shutil.copy(os.path.join(server_source, "fabric-server-launch.jar"), server)
    open(os.path.join(server, "eula.txt"), "w").write("eula=true\n")
    open(os.path.join(server, "server.properties"), "w").write("\n".join([
        "online-mode=false", f"server-port={PORT}", "spawn-protection=0", "level-type=minecraft\\:flat",
        "generate-structures=false", "view-distance=6", "simulation-distance=6", "gamemode=creative",
        "spawn-monsters=false", "motd=Caricraft QA Cobblemon"]) + "\n")
    ops = [{"uuid": offline_uuid(n), "name": n, "level": 4, "bypassesPlayerLimit": False} for n in ("Dev1", "Dev2")]
    json.dump(ops, open(os.path.join(server, "ops.json"), "w"))
    copy_mods(server_source, os.path.join(server, "mods"), [mod_jar], prefixes)
    if role in ROLE_MODS:
        os.makedirs(os.path.join(server, "config"), exist_ok=True)
        for name in ("rctmod-server.toml",):
            if os.path.exists(os.path.join(server_source, "config", name)):
                shutil.copy(os.path.join(server_source, "config", name), os.path.join(server, "config", name))
        # Datapack de entrenadores de COBBLEVERSE (series y level cap); solo en el mundo de prueba local.
        packs = os.path.join(server, "world", "datapacks")
        os.makedirs(packs, exist_ok=True)
        for pack in glob.glob(os.path.join(server_source, "datapacks", "COBBLEVERSE-RCT-DP-*.zip")):
            shutil.copy(pack, packs)
    config_path = os.path.join(server, "config", "caricraft.json")
    os.makedirs(os.path.dirname(config_path), exist_ok=True)
    config = json.load(open(config_path)) if os.path.exists(config_path) else {}
    config.setdefault("cobbleverse", {})["enabled"] = True
    config.setdefault("tips", {})["enabled"] = False
    # La opción del level cap viene desactivada; el rol D la enciende para comprobar que funciona.
    config["cobbleverse"]["levelCapSpawns"] = role == "D"
    # El rol Z prueba la limpieza automática: cada minuto en vez de cada 15.
    config.setdefault("cleaner", {})["intervalMinutes"] = 1 if role == "Z" else 15
    json.dump(config, open(config_path, "w"), indent=2)

    client = os.path.join(work, "client")
    copy_mods(server_source, os.path.join(client, "mods"), [mod_jar, qa_jar], prefixes)
    options = os.path.join(client, "options.txt")
    lines = open(options).read().splitlines() if os.path.exists(options) else []
    lines = [line for line in lines if not line.startswith(("lang:", "onboardAccessibility:", "tutorialStep:"))]
    lines += ["lang:es_es", "onboardAccessibility:false", "tutorialStep:none"]
    open(options, "w").write("\n".join(lines) + "\n")
    return server, client


def wait_for(path, needles, timeout):
    end = time.time() + timeout
    while time.time() < end:
        if os.path.exists(path):
            text = open(path, errors="replace").read()
            for needle in needles:
                if needle in text:
                    return needle
        time.sleep(2)
    return None


def run(server_source, work, role):
    server_source = os.path.abspath(server_source)
    work = os.path.abspath(work)
    server, client = prepare(server_source, work, role)
    logs = os.path.join(work, "logs")
    os.makedirs(logs, exist_ok=True)
    java = shutil.which("java")
    server_log = open(os.path.join(logs, "server.log"), "w")
    server_proc = subprocess.Popen([java, "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=server,
                                   stdin=subprocess.PIPE, stdout=server_log, stderr=subprocess.STDOUT, text=True)
    try:
        ready = wait_for(os.path.join(logs, "server.log"), ["Caricraft Mod listo", "Exception in", "Crash report"], 360)
        if ready != "Caricraft Mod listo":
            sys.exit("el servidor no arrancó; mira logs/server.log")
        command = [java, "-Xmx3G", "-cp", client_classpath(server_source), f"-Dcaricraft.qa.role={role}",
                   "net.fabricmc.loader.impl.launch.knot.KnotClient",
                   "--username", "Dev1", "--uuid", offline_uuid("Dev1"), "--version", MC, "--gameDir", client,
                   "--assetsDir", os.path.join(GRADLE, "fabric-loom/assets"), "--assetIndex", asset_index(),
                   "--accessToken", "0", "--userType", "legacy", "--quickPlayMultiplayer", f"localhost:{PORT}",
                   "--width", "1280", "--height", "720"]
        env = dict(os.environ)
        if not env.get("DISPLAY"):
            env["LIBGL_ALWAYS_SOFTWARE"] = "1"
            command = ["xvfb-run", "-a", "-s", "-screen 0 1280x720x24"] + command
        with open(os.path.join(logs, "client.log"), "w") as client_log:
            subprocess.run(command, cwd=client, env=env, stdout=client_log, stderr=subprocess.STDOUT, timeout=1200)
    finally:
        try:
            server_proc.stdin.write("stop\n")
            server_proc.stdin.flush()
            server_proc.wait(60)
        except Exception:
            server_proc.kill()
        server_log.close()
    qa = [line.split("[QA] ", 1)[1].rstrip() for line in open(os.path.join(logs, "client.log"), errors="replace")
          if "[QA] " in line]
    print("\n".join(qa))
    print("capturas en", os.path.join(client, "screenshots"))


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    run(*sys.argv[1:])
