#!/usr/bin/env python3
"""Offline APK DEX/resource verification; requires Android build-tools aapt2."""
import pathlib
import re
import shutil
import struct
import subprocess
import sys
import zipfile

APK = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path(__file__).resolve().parents[2] / "input-apks/samsung-keyboard-5.9.40.75.apk"
METHODS = {
    ("Lcom/samsung/android/honeyboard/bee/b;", "<init>", "(Landroid/content/Context;LEb/c;)V"),
    ("Lcom/samsung/android/honeyboard/bee/b;", "e", "()Ljava/util/List;"),
    ("Lcom/samsung/android/honeyboard/beehive/data/BeeItem;", "executeInner", "()V"),
    ("Lqa/e;", "a", "(LF6/b;LF6/G;Z)V"),
    ("Lcom/samsung/android/honeyboard/service/HoneyBoardService;", "onKeyDown", "(ILandroid/view/KeyEvent;)Z"),
    ("Lcom/samsung/android/honeyboard/service/HoneyBoardService;", "onUpdateSelection", "(IIIIII)V"),
    ("LL7/b;", "commitText", "(Ljava/lang/CharSequence;I)Z"),
    ("LL7/b;", "setComposingText", "(Ljava/lang/CharSequence;I)Z"),
    ("LL7/b;", "deleteSurroundingText", "(II)Z"),
    ("LL7/b;", "deleteSurroundingTextInCodePoints", "(II)Z"),
    ("LL7/b;", "sendKeyEvent", "(Landroid/view/KeyEvent;)Z"),
    ("La7/e;", "g", "()Lcom/samsung/android/honeyboard/base/languagepack/language/Language;"),
    *(("Lcom/samsung/android/honeyboard/base/languagepack/language/Language;", name, "()Ljava/lang/String;") for name in ("getLanguageCode", "getCountryCode")),
    ("Lna/b;", "initialize", "()V"),
    ("Lna/b;", "n", "(Ljava/lang/String;)I"),
    ("LX9/e;", "r", "()V"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "w", "()V"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "v", "(Ljava/lang/String;Z)V"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "u", "(Lz6/c;Z)Z"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "x", "(ZZZZ)V"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/N;", "e", "()Lcom/samsung/android/honeyboard/beehive/data/BeeObservableArrayList;"),
    ("Lcom/samsung/android/honeyboard/beehive/data/BeeItem;", "getBeeVisibility", "()I"),
    ("Lcom/samsung/android/honeyboard/beehive/data/BeeItem;", "getBeeId", "()Ljava/lang/String;"),
    ("Lna/b;", "j", "(Ljava/util/List;II)V"),
    ("Lia/c;", "p", "()Ljava/lang/String;"),
    ("Lz6/c;", "isSleep", "()Z"),
    ("Lz6/c;", "execute", "()V"),
    ("Lz6/c;", "execute", "(Lkotlin/jvm/functions/Function0;)V"),
    ("Lz6/i;", "<init>", "(Landroid/content/Context;Landroid/graphics/drawable/Icon;ILandroid/content/res/Resources;Ljava/lang/String;)V"),
    ("Lz6/j;", "<init>", "(Lz6/i;)V"),
    *(("Lz6/j;", name, "()Ljava/lang/String;") for name in ("a", "b", "e")),
    ("Lcom/samsung/android/honeyboard/common/beehive/BeeTag;", "<init>", "(Ljava/lang/String;Z)V"),
}
SERVICE = "Lcom/samsung/android/honeyboard/service/HoneyBoardService;"
METHODS.update((SERVICE, name, signature) for name, signature in (
    ("onCreateInputView", "()Landroid/view/View;"),
    ("onStartInput", "(Landroid/view/inputmethod/EditorInfo;Z)V"),
    ("onStartInputView", "(Landroid/view/inputmethod/EditorInfo;Z)V"),
    ("onFinishInputView", "(Z)V"), ("onFinishInput", "()V"),
    ("onWindowHidden", "()V"), ("onDestroy", "()V"),
))
# TranslationViewModel 5.9.40.75: owner and inherited methods, verified against original DEX.
TRANSLATION_VM = "Lcom/samsung/android/honeyboard/textboard/translate/viewmodel/TranslationViewModel;"
TRANSLATION_BASE = "Lcom/samsung/android/honeyboard/textboard/translate/viewmodel/AbsTranslationViewModel;"
METHODS.update({
    (TRANSLATION_VM, "internalTranslate", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function1;)V"),
    (TRANSLATION_VM, "doRunning", "(ZZ)V"),
    (TRANSLATION_VM, "clear", "()V"),
    (TRANSLATION_BASE, "onTextChanged", "(Ljava/lang/CharSequence;III)V"),
    (TRANSLATION_BASE, "setSourceLanguage$HoneyBoard_textBoard_globalRelease", "(LJq/a;Z)V"),
    (TRANSLATION_BASE, "setTargetLanguage$HoneyBoard_textBoard_globalRelease", "(LJq/a;ZZ)V"),
    (TRANSLATION_BASE, "getInputText", "()Ljava/lang/String;"),
    (TRANSLATION_BASE, "getContext", "()Landroid/content/Context;"),
    (TRANSLATION_BASE, "getService", "()Lsb/a;"),
    (TRANSLATION_BASE, "setPrevInputText", "(Ljava/lang/String;)V"),
    ("Lsb/a;", "getWindow", "()Landroid/app/Dialog;"),
    ("Lcom/samsung/android/honeyboard/settings/aiwriter/translation/WritingAssistTranslationSettingsFragment;",
     "onCreatePreferences", "(Landroid/os/Bundle;Ljava/lang/String;)V"),
})
# IDs queried through Resources.getIdentifier; check the compiled APK, not
# decompiler-generated XML (which may not match the installed binary).
RESOURCES = {
    ("id", "layout_honey_frame"), ("id", "layout_header"),
    ("id", "layout_toolbar"), ("id", "layout_body"),
    ("id", "layout_board_left"), ("id", "layout_board_right"),
    ("id", "beehive_primary_constraint_layout"),
    ("dimen", "header_layout_area_height"),
    ("attr", "beehive_primary_background"),
}
FIELDS = {
    ("Lcom/samsung/android/honeyboard/bee/b;", "d", "Landroidx/databinding/ObservableArrayList;"),
    ("Lcom/samsung/android/honeyboard/bee/b;", "a", "Landroid/content/Context;"),
    ("LX9/e;", "n", "Ljava/util/List;"),
    ("LX9/e;", "k", "Lcom/samsung/android/honeyboard/beehive/viewmodel/K;"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "a", "Lna/a;"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "t", "Lcom/samsung/android/honeyboard/beehive/data/BeeObservableArrayList;"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "n", "Lcom/samsung/android/honeyboard/beehive/viewmodel/N;"),
    ("Lcom/samsung/android/honeyboard/beehive/viewmodel/K;", "o", "Lcom/samsung/android/honeyboard/beehive/viewmodel/N;"),
    ("Lqa/e;", "D", "LF6/b;"), ("Lqa/e;", "E", "LF6/G;"),
    ("Lna/b;", "d", "Lna/a;"), ("Lna/c;", "b", "Lia/c;"),
    ("Lz6/i;", "h", "Ljava/lang/String;"), ("Lz6/i;", "i", "Z"),
    (SERVICE, "viewHolderLayout", "Landroid/view/View;"),
}

def uleb(data, pos):
    value = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 127) << shift
        if byte < 128:
            return value, pos
        shift += 7

def tables(data):
    u32 = lambda pos: struct.unpack_from("<I", data, pos)[0]
    ns, so, nt, to, np, po, nf, fo, nm, mo = struct.unpack_from("<10I", data, 56)
    strings = []
    for index in range(ns):
        _, pos = uleb(data, u32(so + 4 * index))
        strings.append(data[pos:data.index(0, pos)].decode("utf-8", "replace"))
    types = [strings[u32(to + 4 * index)] for index in range(nt)]
    fields, methods = set(), set()
    for index in range(nf):
        cls, typ, name = struct.unpack_from("<HHI", data, fo + 8 * index)
        fields.add((types[cls], strings[name], types[typ]))
    for index in range(nm):
        cls, proto, name = struct.unpack_from("<HHI", data, mo + 8 * index)
        _, ret, params = struct.unpack_from("<III", data, po + 12 * proto)
        args = [] if not params else [types[struct.unpack_from("<H", data, params + 4 + 2 * j)[0]] for j in range(u32(params))]
        methods.add((types[cls], strings[name], "(" + "".join(args) + ")" + types[ret]))
    return fields, methods

fields, methods = set(), set()
with zipfile.ZipFile(APK) as apk:
    for name in apk.namelist():
        if name.startswith("classes") and name.endswith(".dex"):
            f, m = tables(apk.read(name))
            fields.update(f)
            methods.update(m)
missing = [("field", value) for value in FIELDS - fields] + [("method", value) for value in METHODS - methods]
if not shutil.which("aapt2"):
    print("ERROR aapt2 is required to verify APK resources", file=sys.stderr)
    sys.exit(2)
resource_dump = subprocess.run(["aapt2", "dump", "resources", str(APK)],
                               capture_output=True, text=True, check=True).stdout
compiled = set(re.findall(r"^\s*resource 0x[0-9a-fA-F]+ (id|dimen|attr)/([\w.]+)",
                          resource_dump, re.MULTILINE))
missing.extend(("resource", value) for value in RESOURCES - compiled)
for kind, value in sorted(missing):
    print("MISSING", kind, *value)
print(f"APK symbol references: {len(FIELDS)} fields, {len(METHODS)} methods, "
      f"{len(RESOURCES)} resources; {len(missing)} missing")
print("This verifies original DEX descriptors and APK resource names, not hook execution or runtime semantics.")
sys.exit(bool(missing))
