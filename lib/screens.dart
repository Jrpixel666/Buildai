import 'dart:async';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'ai.dart';
import 'github.dart';
import 'storage.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  Settings? settings;
  int tab = 0;
  final projectsKey = GlobalKey<_ProjectsTabState>();

  @override
  void initState() {
    super.initState();
    Settings.load().then((s) => setState(() => settings = s));
  }

  @override
  Widget build(BuildContext context) {
    final s = settings;
    if (s == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final pages = [
      IdeaTab(settings: s, onCreated: () => setState(() => tab = 2)),
      ModelsTab(settings: s, onPicked: () => setState(() {})),
      ProjectsTab(key: projectsKey, settings: s),
      SettingsTab(settings: s),
    ];
    return Scaffold(
      body: SafeArea(child: IndexedStack(index: tab, children: pages)),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) {
          setState(() => tab = i);
          if (i == 2) projectsKey.currentState?.reload();
        },
        destinations: const [
          NavigationDestination(icon: Icon(Icons.lightbulb_outline), label: 'Idea'),
          NavigationDestination(icon: Icon(Icons.hub_outlined), label: 'Models'),
          NavigationDestination(icon: Icon(Icons.folder_outlined), label: 'Projects'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), label: 'Settings'),
        ],
      ),
    );
  }
}

void toast(BuildContext c, String msg) =>
    ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(msg)));

String _slug(String text) {
  var s = text
      .toLowerCase()
      .replaceAll(RegExp(r'[^a-z0-9]+'), '_')
      .replaceAll(RegExp(r'^_+|_+$'), '');
  if (s.isEmpty) s = 'my_app';
  if (RegExp(r'^[0-9]').hasMatch(s)) s = 'app_$s';
  return s.length > 30 ? s.substring(0, 30).replaceAll(RegExp(r'_+$'), '') : s;
}

// ───────────────────────────── Idea ─────────────────────────────

class IdeaTab extends StatefulWidget {
  final Settings settings;
  final VoidCallback onCreated;
  const IdeaTab({super.key, required this.settings, required this.onCreated});
  @override
  State<IdeaTab> createState() => _IdeaTabState();
}

class _IdeaTabState extends State<IdeaTab> {
  final name = TextEditingController();
  final idea = TextEditingController();
  bool busy = false;

  Future<void> _build() async {
    if (idea.text.trim().isEmpty) return toast(context, 'Describe your app first.');
    setState(() => busy = true);
    try {
      final appName = _slug(name.text.isEmpty ? idea.text.split(' ').take(3).join(' ') : name.text);
      final files = await AiService(widget.settings).generate(appName, idea.text.trim());
      await ProjectStore.save(Project(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        name: appName,
        idea: idea.text.trim(),
        files: files,
        updated: DateTime.now(),
      ));
      if (!mounted) return;
      toast(context, 'Created $appName with ${files.length} files');
      idea.clear();
      name.clear();
      widget.onCreated();
    } catch (e) {
      if (mounted) toast(context, '$e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.settings;
    final using = s.mode == AiMode.cloud ? s.model : 'On-device: ${s.localModel}';
    return ListView(padding: const EdgeInsets.all(16), children: [
      Text('What do you want to build?', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 4),
      Text('AI: $using', style: Theme.of(context).textTheme.bodySmall),
      const SizedBox(height: 16),
      TextField(
        controller: name,
        decoration: const InputDecoration(labelText: 'App name (optional)', border: OutlineInputBorder()),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: idea,
        minLines: 8,
        maxLines: 20,
        decoration: const InputDecoration(
          hintText: 'e.g. A habit tracker with daily check-ins, streaks, a calendar view and dark mode. Save data on the phone.',
          border: OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 16),
      FilledButton.icon(
        onPressed: busy ? null : _build,
        icon: busy
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.auto_awesome),
        label: Text(busy ? 'Writing your app… (can take a few minutes)' : 'Generate app'),
      ),
    ]);
  }
}

// ───────────────────────────── Models ─────────────────────────────

class ModelsTab extends StatefulWidget {
  final Settings settings;
  final VoidCallback onPicked;
  const ModelsTab({super.key, required this.settings, required this.onPicked});
  @override
  State<ModelsTab> createState() => _ModelsTabState();
}

class _ModelsTabState extends State<ModelsTab> {
  final q = TextEditingController(text: 'coder instruct');
  List<HfModel> results = [];
  bool busy = false;
  bool gguf = false;

  @override
  void initState() {
    super.initState();
    _search();
  }

  Future<void> _search() async {
    setState(() => busy = true);
    try {
      results = await AiService(widget.settings).searchModels(q.text, ggufOnly: gguf);
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
    if (mounted) setState(() => busy = false);
  }

  Future<void> _pick(HfModel m) async {
    final s = widget.settings;
    if (gguf) {
      final files = await AiService(s).ggufFiles(m.id);
      if (!mounted) return;
      showModalBottomSheet(
        context: context,
        builder: (_) => ListView(padding: const EdgeInsets.all(16), children: [
          Text('Run on the phone', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text('Pick a file (Q4_K_M is a good balance, 1–3B models suit most phones). '
              'It downloads in your browser; the README shows how to run it in Termux.'),
          ...files.map((f) => ListTile(
                title: Text(f),
                trailing: const Icon(Icons.download),
                onTap: () {
                  final url = 'https://huggingface.co/${m.id}/resolve/main/$f';
                  launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
                },
              )),
        ]),
      );
      return;
    }
    s.model = m.id;
    s.mode = AiMode.cloud;
    await s.save();
    widget.onPicked();
    if (mounted) toast(context, 'Using ${m.id}');
  }

  @override
  Widget build(BuildContext context) {
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
        child: TextField(
          controller: q,
          textInputAction: TextInputAction.search,
          onSubmitted: (_) => _search(),
          decoration: InputDecoration(
            labelText: 'Search Hugging Face',
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(icon: const Icon(Icons.search), onPressed: _search),
          ),
        ),
      ),
      SwitchListTile(
        title: const Text('On-device models (GGUF)'),
        subtitle: const Text('Off: models you can use online now'),
        value: gguf,
        onChanged: (v) {
          setState(() => gguf = v);
          _search();
        },
      ),
      if (busy) const LinearProgressIndicator(),
      Expanded(
        child: ListView.builder(
          itemCount: results.length,
          itemBuilder: (_, i) {
            final m = results[i];
            final selected = !gguf && m.id == widget.settings.model;
            return ListTile(
              title: Text(m.id),
              subtitle: Text('⬇ ${m.downloads}   ♥ ${m.likes}'),
              trailing: selected ? const Icon(Icons.check_circle, color: Colors.green) : null,
              onTap: () => _pick(m),
            );
          },
        ),
      ),
      const Padding(
        padding: EdgeInsets.all(8),
        child: Text('Tip: not every model is served online. If one fails, try a popular "Instruct" coder model.',
            style: TextStyle(fontSize: 12)),
      ),
    ]);
  }
}

// ───────────────────────────── Projects ─────────────────────────────

class ProjectsTab extends StatefulWidget {
  final Settings settings;
  const ProjectsTab({super.key, required this.settings});
  @override
  State<ProjectsTab> createState() => _ProjectsTabState();
}

class _ProjectsTabState extends State<ProjectsTab> {
  List<Project> list = [];

  @override
  void initState() {
    super.initState();
    reload();
  }

  Future<void> reload() async {
    final l = await ProjectStore.all();
    if (mounted) setState(() => list = l);
  }

  @override
  Widget build(BuildContext context) {
    if (list.isEmpty) {
      return const Center(child: Text('No projects yet. Start on the Idea tab.'));
    }
    return RefreshIndicator(
      onRefresh: reload,
      child: ListView(
        children: list
            .map((p) => ListTile(
                  leading: const Icon(Icons.android),
                  title: Text(p.name),
                  subtitle: Text(p.idea, maxLines: 2, overflow: TextOverflow.ellipsis),
                  onTap: () async {
                    await Navigator.push(context,
                        MaterialPageRoute(builder: (_) => ProjectScreen(project: p, settings: widget.settings)));
                    reload();
                  },
                ))
            .toList(),
      ),
    );
  }
}

class ProjectScreen extends StatefulWidget {
  final Project project;
  final Settings settings;
  const ProjectScreen({super.key, required this.project, required this.settings});
  @override
  State<ProjectScreen> createState() => _ProjectScreenState();
}

class _ProjectScreenState extends State<ProjectScreen> {
  Project get p => widget.project;
  String status = '';
  bool busy = false;
  BuildStatus? last;
  Timer? poll;

  @override
  void dispose() {
    poll?.cancel();
    super.dispose();
  }

  Future<void> _run(String label, Future<void> Function() job) async {
    setState(() {
      busy = true;
      status = label;
    });
    try {
      await job();
    } catch (e) {
      if (mounted) setState(() => status = 'Error: $e');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  void _merge(Map<String, String> changed) {
    p.files.addAll(changed);
    ProjectStore.save(p);
    setState(() => status = 'Updated ${changed.length} file(s)');
  }

  Future<void> _refine() async {
    final c = TextEditingController();
    final req = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Ask AI for changes'),
        content: TextField(controller: c, maxLines: 6, autofocus: true,
            decoration: const InputDecoration(hintText: 'e.g. Add a settings page with a dark mode toggle')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, c.text), child: const Text('Apply')),
        ],
      ),
    );
    if (req == null || req.trim().isEmpty) return;
    await _run('AI is editing…', () async {
      _merge(await AiService(widget.settings).refine(p.name, p.files, req));
    });
  }

  Future<void> _build() async {
    await _run('Pushing to GitHub…', () async {
      final gh = GitHubService(widget.settings);
      final sha = await gh.push(p);
      setState(() => status = 'Building on GitHub (about 5–8 minutes)…');
      poll?.cancel();
      poll = Timer.periodic(const Duration(seconds: 20), (_) async {
        try {
          final st = await gh.status(sha, p.name);
          if (!mounted) return;
          setState(() {
            last = st;
            status = switch (st.state) {
              'success' => 'APK ready!',
              'failure' => 'Build failed. Tap "Fix with AI".',
              _ => 'Building on GitHub (${st.state})…',
            };
          });
          if (st.state == 'success' || st.state == 'failure') {
            poll?.cancel();
            if (st.releaseUrl != null) {
              p.lastReleaseUrl = st.releaseUrl;
              ProjectStore.save(p);
            }
          }
        } catch (_) {}
      });
    });
  }

  Future<void> _fix() async {
    final log = last?.errorLog ?? '';
    await _run('AI is fixing the build error…', () async {
      _merge(await AiService(widget.settings).fix(p.name, p.files, log));
    });
    if (mounted && !status.startsWith('Error')) _build();
  }

  void _open(String? url) {
    if (url != null) launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final paths = p.files.keys.toList()..sort();
    return Scaffold(
      appBar: AppBar(title: Text(p.name), actions: [
        IconButton(
          icon: const Icon(Icons.delete_outline),
          onPressed: () async {
            await ProjectStore.delete(p.id);
            if (context.mounted) Navigator.pop(context);
          },
        ),
      ]),
      body: ListView(children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Wrap(spacing: 8, runSpacing: 8, children: [
            FilledButton.icon(onPressed: busy ? null : _build, icon: const Icon(Icons.build), label: const Text('Build APK')),
            OutlinedButton.icon(onPressed: busy ? null : _refine, icon: const Icon(Icons.auto_awesome), label: const Text('Ask AI')),
            if (last?.state == 'failure')
              OutlinedButton.icon(onPressed: busy ? null : _fix, icon: const Icon(Icons.healing), label: const Text('Fix with AI')),
            if (last?.apkUrl != null)
              FilledButton.tonalIcon(onPressed: () => _open(last!.apkUrl), icon: const Icon(Icons.download), label: const Text('Download APK')),
            if (last?.runUrl != null)
              TextButton(onPressed: () => _open(last!.runUrl), child: const Text('Build log')),
            if (last == null && p.lastReleaseUrl != null)
              TextButton(onPressed: () => _open(p.lastReleaseUrl), child: const Text('Last APK')),
          ]),
        ),
        if (status.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              if (busy || (last != null && last!.state != 'success' && last!.state != 'failure' && poll?.isActive == true))
                const Padding(padding: EdgeInsets.only(right: 8),
                    child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))),
              Expanded(child: Text(status)),
            ]),
          ),
        const Divider(height: 32),
        ...paths.map((path) => ListTile(
              dense: true,
              leading: const Icon(Icons.description_outlined),
              title: Text(path),
              onTap: () async {
                await Navigator.push(context, MaterialPageRoute(builder: (_) => EditorScreen(project: p, path: path)));
                setState(() {});
              },
            )),
        ListTile(
          leading: const Icon(Icons.add),
          title: const Text('New file'),
          onTap: () async {
            final c = TextEditingController(text: 'lib/');
            final path = await showDialog<String>(
              context: context,
              builder: (_) => AlertDialog(
                title: const Text('File path'),
                content: TextField(controller: c, autofocus: true),
                actions: [FilledButton(onPressed: () => Navigator.pop(context, c.text.trim()), child: const Text('Create'))],
              ),
            );
            if (path == null || path.isEmpty || path.contains('..')) return;
            p.files.putIfAbsent(path, () => '');
            await ProjectStore.save(p);
            setState(() {});
          },
        ),
      ]),
    );
  }
}

class EditorScreen extends StatefulWidget {
  final Project project;
  final String path;
  const EditorScreen({super.key, required this.project, required this.path});
  @override
  State<EditorScreen> createState() => _EditorScreenState();
}

class _EditorScreenState extends State<EditorScreen> {
  late final c = TextEditingController(text: widget.project.files[widget.path]);
  bool dirty = false;

  Future<void> _save() async {
    widget.project.files[widget.path] = c.text;
    await ProjectStore.save(widget.project);
    setState(() => dirty = false);
    if (mounted) toast(context, 'Saved');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.path, style: const TextStyle(fontSize: 15)),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: () async {
              widget.project.files.remove(widget.path);
              await ProjectStore.save(widget.project);
              if (context.mounted) Navigator.pop(context);
            },
          ),
          IconButton(icon: Icon(Icons.save, color: dirty ? Colors.orange : null), onPressed: _save),
        ],
      ),
      body: TextField(
        controller: c,
        expands: true,
        maxLines: null,
        keyboardType: TextInputType.multiline,
        style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
        decoration: const InputDecoration(border: InputBorder.none, contentPadding: EdgeInsets.all(12)),
        onChanged: (_) {
          if (!dirty) setState(() => dirty = true);
        },
      ),
    );
  }
}

// ───────────────────────────── Settings ─────────────────────────────

class SettingsTab extends StatefulWidget {
  final Settings settings;
  const SettingsTab({super.key, required this.settings});
  @override
  State<SettingsTab> createState() => _SettingsTabState();
}

class _SettingsTabState extends State<SettingsTab> {
  Settings get s => widget.settings;
  late final hf = TextEditingController(text: s.hfToken);
  late final gh = TextEditingController(text: s.githubToken);
  late final repo = TextEditingController(text: s.githubRepo);
  late final model = TextEditingController(text: s.model);
  late final localUrl = TextEditingController(text: s.localUrl);
  late final localModel = TextEditingController(text: s.localModel);

  Future<void> _save() async {
    s
      ..hfToken = hf.text.trim()
      ..githubToken = gh.text.trim()
      ..githubRepo = repo.text.trim()
      ..model = model.text.trim()
      ..localUrl = localUrl.text.trim()
      ..localModel = localModel.text.trim();
    await s.save();
    if (mounted) toast(context, 'Settings saved');
  }

  Future<void> _browseLocalModels() async {
    s.localUrl = localUrl.text.trim();
    try {
      final models = await AiService(s).localModels();
      if (!mounted) return;
      if (models.isEmpty) {
        toast(context, 'The server reported no models.');
        return;
      }
      final picked = await showModalBottomSheet<String>(
        context: context,
        builder: (_) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('Models on ${s.localUrl}', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            ...models.map((m) => ListTile(
                  title: Text(m),
                  trailing: m == localModel.text ? const Icon(Icons.check_circle, color: Colors.green) : null,
                  onTap: () => Navigator.pop(context, m),
                )),
          ],
        ),
      );
      if (picked != null) setState(() => localModel.text = picked);
    } catch (e) {
      if (mounted) toast(context, '$e');
    }
  }

  Widget _f(TextEditingController c, String label, {bool secret = false, String? hint}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(
          controller: c,
          obscureText: secret,
          decoration: InputDecoration(labelText: label, hintText: hint, border: const OutlineInputBorder()),
        ),
      );

  @override
  Widget build(BuildContext context) {
    model.text = s.model; // reflect picks from the Models tab
    return ListView(padding: const EdgeInsets.all(16), children: [
      Text('AI', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      SegmentedButton<AiMode>(
        segments: const [
          ButtonSegment(value: AiMode.cloud, label: Text('Online (HF)'), icon: Icon(Icons.cloud)),
          ButtonSegment(value: AiMode.local, label: Text('On-device'), icon: Icon(Icons.phone_android)),
        ],
        selected: {s.mode},
        onSelectionChanged: (v) => setState(() => s.mode = v.first),
      ),
      const SizedBox(height: 16),
      if (s.mode == AiMode.cloud) ...[
        _f(hf, 'Hugging Face token', secret: true, hint: 'hf_…'),
        _f(model, 'Model id'),
      ] else ...[
        _f(localUrl, 'Local server URL'),
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: TextField(
                controller: localModel,
                decoration: const InputDecoration(labelText: 'Local model name', border: OutlineInputBorder()),
              ),
            ),
            const SizedBox(width: 8),
            IconButton.filledTonal(
              onPressed: _browseLocalModels,
              icon: const Icon(Icons.search),
              tooltip: 'Browse models on the local server',
            ),
          ]),
        ),
        const Text(
            'Tap the search button to list models already available on your Termux server, '
            'or type the name by hand. Runs through llama.cpp or Ollama in Termux. See README.',
            style: TextStyle(fontSize: 12)),
        const SizedBox(height: 12),
        _f(hf, 'Hugging Face token (for searching)', secret: true),
      ],
      const Divider(height: 32),
      Text('GitHub builds', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      _f(gh, 'GitHub token', secret: true, hint: 'needs repo + workflow access'),
      _f(repo, 'Repository', hint: 'yourname/my-ai-apps'),
      FilledButton(onPressed: _save, child: const Text('Save')),
    ]);
  }
}
