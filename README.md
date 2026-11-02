# AI App Builder

An Android app that turns an idea into a Flutter app. It searches Hugging Face for a model, writes the code, lets you edit it, and gets GitHub to build the APK for you.

## 1. Get the builder APK (one time, about 10 minutes)

1. On GitHub, create a new repository called `ai-app-builder` and tick **Add a README**.
2. Upload every file in this folder to it, keeping the folders (`lib/`, `.github/workflows/`). On a phone, the easiest way is GitHub's website: **Add file → Upload files**. If the `.github` folder won't upload, create the file by hand with **Add file → Create new file**, name it `.github/workflows/build.yml`, and paste in the contents.
3. Open the **Actions** tab. The "Build APK" run starts on its own and takes about 5–8 minutes.
4. When it's green, go to **Releases** and download `app-release.apk`. Install it, allowing "install unknown apps" for your browser if asked.

## 2. Tokens (one time)

**Hugging Face token:** huggingface.co → Settings → Access Tokens → New token. Choose *Fine-grained* and tick **Make calls to Inference Providers**. Free accounts get a small monthly credit; a PRO account gets more.

**GitHub token:** github.com → Settings → Developer settings → Personal access tokens → **Tokens (classic)** → Generate. Tick **repo** and **workflow**. (The workflow permission is required because each build pushes the build file.)

**Repo for your generated apps:** create one more repository (for example `my-ai-apps`) with a README. Each app you make goes on its own branch, `app/<name>`, and its APKs appear under that repo's Releases.

Put all three into the app's **Settings** tab and tap Save.

## 3. Using it

- **Models tab:** search Hugging Face and tap a model to use it. Coder "Instruct" models work best, for example `Qwen/Qwen2.5-Coder-32B-Instruct`. Not every model is served online; if one errors, pick a more popular one.
- **Idea tab:** describe the app and tap **Generate app**.
- **Projects tab:** open a project to view or edit files, **Ask AI** for changes, and **Build APK**. When the build finishes, tap **Download APK**. If it fails, tap **Fix with AI**: it reads the error log, patches the code and rebuilds.

## 4. On-device AI (optional, offline)

Phones can run small models (about 1–3B parameters) through Termux. Expect slower, weaker code than online models.

1. Install **Termux from F-Droid** (the Play Store version is outdated).
2. In Termux:
   ```
   pkg update && pkg install llama-cpp
   termux-setup-storage
   ```
3. In the builder's **Models** tab, switch on **On-device models (GGUF)**, search, for example `qwen2.5-coder 1.5b instruct gguf`, and download a `Q4_K_M` file.
4. Start the server (adjust the file name):
   ```
   llama-server -m ~/storage/downloads/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf -c 8192 --port 8080
   ```
5. In **Settings**, choose **On-device**. Leave the URL as `http://127.0.0.1:8080/v1` and keep Termux running in the background.

(Ollama in Termux also works: use `http://127.0.0.1:11434/v1` and the Ollama model name.)

## How it fits together

- `lib/ai.dart`: Hugging Face model search and chat (online router or local server), plus code generation, editing and fixing
- `lib/github.dart`: pushes a project as one commit and watches the build
- `lib/screens.dart`: all screens
- `.github/workflows/build.yml`: creates the Android files, builds the APK, and publishes it as a Release (also embedded in `lib/workflow.dart`, so it's pushed with every app)

Note: APKs are signed with Flutter's debug key, which is fine for installing on your own phone but not for the Play Store.
