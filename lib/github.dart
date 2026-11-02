import 'dart:convert';
import 'package:http/http.dart' as http;
import 'storage.dart';
import 'workflow.dart';

class BuildStatus {
  final String state; // queued, in_progress, success, failure, none
  final String? runUrl;
  final String? apkUrl;
  final String? releaseUrl;
  final String? errorLog;
  BuildStatus(this.state,
      {this.runUrl, this.apkUrl, this.releaseUrl, this.errorLog});
}

class GitHubService {
  final Settings s;
  GitHubService(this.s);

  String get _repo => s.githubRepo.trim();
  Map<String, String> get _h => {
        'Authorization': 'Bearer ${s.githubToken}',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'Content-Type': 'application/json',
      };
  Uri _u(String path) => Uri.parse('https://api.github.com/repos/$_repo$path');

  void _check() {
    if (s.githubToken.isEmpty || !_repo.contains('/')) {
      throw Exception('Add your GitHub token and repo (owner/repo) in Settings.');
    }
  }

  Future<dynamic> _get(String path) async {
    final r = await http.get(_u(path), headers: _h);
    if (r.statusCode == 404) return null;
    if (r.statusCode >= 300) {
      throw Exception('GitHub ${r.statusCode}: ${r.body}');
    }
    return jsonDecode(r.body);
  }

  Future<dynamic> _send(String method, String path, Object body) async {
    final req = http.Request(method, _u(path))
      ..headers.addAll(_h)
      ..body = jsonEncode(body);
    final r = await http.Response.fromStream(await req.send());
    if (r.statusCode >= 300) {
      var msg = r.body;
      if (r.statusCode == 409 && msg.contains('empty')) {
        msg = 'Your repo is empty. Create it on GitHub with a README first.';
      } else if (r.statusCode == 404 || r.statusCode == 403) {
        msg = 'GitHub refused access ($msg). Check the token has "repo" and '
            '"workflow" permissions and the repo name is right.';
      }
      throw Exception(msg);
    }
    return jsonDecode(r.body);
  }

  /// Pushes the project (plus the build workflow) as one commit to its branch.
  /// GitHub Actions then builds the APK automatically. Returns the commit sha.
  Future<String> push(Project p) async {
    _check();
    final all = Map<String, String>.from(p.files)
      ..['.github/workflows/build.yml'] = buildWorkflow;

    // Find a parent commit: the branch itself, or the repo's default branch.
    String? parent;
    final ref = await _get('/git/ref/heads/${p.branch}');
    if (ref != null) {
      parent = ref['object']['sha'];
    } else {
      final repo = await _get('');
      if (repo == null) throw Exception('Repo $_repo not found.');
      final def = await _get('/git/ref/heads/${repo['default_branch']}');
      parent = def?['object']['sha'];
    }

    final tree = <Map<String, dynamic>>[];
    for (final e in all.entries) {
      final blob = await _send('POST', '/git/blobs',
          {'content': e.value, 'encoding': 'utf-8'});
      tree.add({
        'path': e.key,
        'mode': '100644',
        'type': 'blob',
        'sha': blob['sha'],
      });
    }
    final t = await _send('POST', '/git/trees', {'tree': tree});
    final c = await _send('POST', '/git/commits', {
      'message': 'AI App Builder: ${p.name}',
      'tree': t['sha'],
      if (parent != null) 'parents': [parent],
    });
    final sha = c['sha'] as String;
    if (ref != null) {
      await _send('PATCH', '/git/refs/heads/${p.branch}',
          {'sha': sha, 'force': true});
    } else {
      await _send(
          'POST', '/git/refs', {'ref': 'refs/heads/${p.branch}', 'sha': sha});
    }
    return sha;
  }

  /// Checks the build for a commit. Call repeatedly until success/failure.
  Future<BuildStatus> status(String sha, String appName) async {
    _check();
    final runs = await _get('/actions/runs?head_sha=$sha&per_page=5');
    final list = (runs?['workflow_runs'] as List?) ?? [];
    if (list.isEmpty) return BuildStatus('queued');
    final run = list.first;
    final url = run['html_url'] as String?;
    if (run['status'] != 'completed') {
      return BuildStatus(run['status'] ?? 'in_progress', runUrl: url);
    }
    if (run['conclusion'] == 'success') {
      final tag = '$appName-build-${run['run_number']}';
      final rel = await _get('/releases/tags/$tag');
      final assets = (rel?['assets'] as List?) ?? [];
      return BuildStatus('success',
          runUrl: url,
          releaseUrl: rel?['html_url'],
          apkUrl: assets.isNotEmpty
              ? assets.first['browser_download_url']
              : null);
    }
    return BuildStatus('failure',
        runUrl: url, errorLog: await _failureLog(run['id']));
  }

  /// Pulls the useful part of the failed job's log so the AI can fix it.
  Future<String> _failureLog(int runId) async {
    try {
      final jobs = await _get('/actions/runs/$runId/jobs');
      final job = (jobs['jobs'] as List).firstWhere(
          (j) => j['conclusion'] == 'failure',
          orElse: () => jobs['jobs'][0]);
      final req = http.Request('GET', _u('/actions/jobs/${job['id']}/logs'))
        ..headers.addAll(_h)
        ..followRedirects = false;
      final first = await http.Response.fromStream(await req.send());
      String text = first.body;
      final loc = first.headers['location'];
      if (loc != null) text = (await http.get(Uri.parse(loc))).body;
      final lines = text.split('\n');
      final errs = lines
          .where((l) =>
              l.contains('Error') ||
              l.contains('error:') ||
              l.contains('.dart:') ||
              l.contains('FAILURE'))
          .take(60)
          .toList();
      final picked = errs.isNotEmpty
          ? errs
          : lines.sublist(lines.length > 80 ? lines.length - 80 : 0);
      // Drop the timestamp prefix GitHub adds to every line.
      return picked
          .map((l) => l.replaceFirst(RegExp(r'^\S+Z '), ''))
          .join('\n');
    } catch (e) {
      return 'Could not read the build log: $e';
    }
  }
}
