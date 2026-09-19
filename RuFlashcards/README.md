# Orosz Szókártyák

Android alkalmazás: kijelölsz egy orosz szót a Chrome böngészőben → megosztod az
appnak → a szó bekerül egy listába → amikor kész vagy, a lista egyben elmegy egy
AI-nak (Claude / OpenAI / Gemini — te választod), ami visszaadja a szótári alakot
és a magyar fordítást → ezekből lapozható szókártyák lesznek az appban.

Nincs szükség Android Studio-ra. Az APK-t a **GitHub Actions** fordítja le neked,
felhőben, ingyen.

## 1. lépés — GitHub fiók és repó

1. Ha még nincs, csinálj egy ingyenes fiókot: https://github.com/signup
2. Jelentkezz be, majd jobb felül a `+` ikonnal hozz létre egy új repository-t
   (pl. `ru-flashcards`). Lehet publikus vagy privát, mindegy.
3. **Ne** pipáld ki a "README hozzáadása" opciót, üres repót hozz létre.

## 2. lépés — a projekt fájljainak feltöltése

1. A kapott csomagot (`RuFlashcards.zip`) csomagold ki a saját gépeden.
2. A GitHub repód oldalán kattints: **Add file → Upload files**.
3. A kicsomagolt `RuFlashcards` mappa **teljes tartalmát** húzd be a böngészőbe
   (a mappastruktúra megmarad, ha az egész mappát húzod be, nem csak pár fájlt).
   Fontos, hogy a `.github` mappa is felkerüljön — ez rejtett mappa, ha a fájlkezelőd
   nem mutatja, kapcsold be a rejtett fájlok megjelenítését.
4. Alul kattints **Commit changes**-re.

## 3. lépés — a build automatikus elindítása

A feltöltés (commit) automatikusan elindítja a buildet. Ellenőrzés:

1. Menj a repód **Actions** fülére.
2. Látnod kell egy "Build APK" nevű futást, sárga (fut) vagy zöld (kész) jelzéssel.
3. Ha esetleg nem indult el automatikusan, kattints balra a "Build APK" workflow-ra,
   majd jobbra fent **Run workflow → Run workflow**.
4. A build kb. 3-6 percet vesz igénybe.

## 4. lépés — az APK letöltése

1. Amikor a futás zöld pipát kap, kattints rá.
2. Legalul, az **Artifacts** részben találod: `app-debug-apk` — ezt töltsd le
   (ez egy .zip, benne az `app-debug.apk`).
3. Csomagold ki a telefonodon (vagy gépen, majd másold át a telefonra).

## 5. lépés — telepítés a telefonon

1. Nyisd meg az `app-debug.apk` fájlt a telefonod fájlkezelőjében.
2. Ha kéri, engedélyezd az "Ismeretlen forrásból telepítés"-t az adott alkalmazásnak
   (pl. Fájlok / Chrome — Android megkérdezi, engedélyezd).
3. Telepítsd. Ez egy ún. "debug" build, saját telepítésre tökéletes, csak a Play
   Store-ba nem tölthető fel ilyen formában.

## 6. lépés — AI API kulcs beállítása az appban

1. Nyisd meg az appot, menj a **Beállítások** fülre.
2. Válaszd ki, melyik AI-t használod (Claude, OpenAI vagy Gemini), és illeszd be
   a saját API kulcsod:
   - Claude (Anthropic): https://console.anthropic.com → API Keys
   - OpenAI: https://platform.openai.com/api-keys
   - Gemini: https://aistudio.google.com/apikey
3. Mentés.

## Használat

1. Chrome-ban jelölj ki egy orosz szót (hosszan nyomva → nyilak a szó két végén
   húzhatók, ha kell) → megjelenik egy menü → **Megosztás** → válaszd az
   **Orosz Szókártyák** appot.
2. A szó bekerül a lista fülre. Ismételd meg minden szónál, amit ki akarsz gyűjteni.
3. Amikor kész a lista, nyisd meg az appot, a **Szólista** fülön nyomd meg a
   **"Kártyák generálása"** gombot — ez elküldi a teljes listát az AI-nak.
4. Pár másodperc múlva a **Kártyák** fülön megjelennek a lapozható szókártyák:
   elöl a szótári alak, koppintásra megfordul és mutatja a magyar fordítást.

## Megjegyzés a frissítésekhez

Ha később bármit módosítasz a projektben (pl. én adok hozzá új kódot), elég
ugyanígy feltöltened az érintett fájlokat a GitHub repóba — minden feltöltés
(commit) újra elindítja a buildet, és mindig a legfrissebb APK-t találod az
Actions → legutóbbi futás → Artifacts alatt.
