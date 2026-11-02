import 'dart:convert';
import 'package:http/http.dart' as http;
import 'storage.dart';

class HfModel {
  final String id;
  final int downloads;
  final int likes;
  final String? pipeline;
  HfModel(this.id, this.downloads, this.likes, this.pipeline);
}

class AiService {
  final Settings s;
  AiService(this.s);

  /// Search the Hugging Face Hub for text-generation models.
  Future<List<HfModel>> searchModels(String query,
      {bool ggufOnly = false}) async {
    final params = {
      'search': query,
      'pipeline_tag': 'text-generation',
      'sort': 'downloads',
      'direction': '-1',
      'limit': '40',
      if (ggufOnly) 'filter': 'gguf',
    };
    final uri = Uri.https('huggingface.co', '/api/models', params);
    final res = await http.get(uri, headers: {
      if (s.hfToken.isNotEmpty) 'Authorization': 'Bearer ${s.hfToken}',
    });
    if (res.statusCode != 200) {
      throw Exception('Hugging Face search failed (${res.statusCode})');
    }
    final list = jsonDecode(res.body) as List;
    return list
        .map((m) => HfModel(
              m['id'] ?? m['modelId'],
              (m['downloads'] ?? 0) as int,
              (m['likes'] ?? 0) as int,
              m['pipeline_tag'],
            ))
        .toList();
  }

  /// GGUF files inside a repo, for downloading into a local server (Termux).
  Future<List<String>> ggufFiles(String modelId) async {
    final res =
        await http.get(Uri.parse('https://huggingface.co/api/models/$modelId'));
    if (res.statusCode != 200) return [];
    final siblings = (jsonDecode(res.body)['siblings'] as List?) ?? [];
    return siblings
        .map((e) => e['rfilename'] as String)
        .where((f) => f.toLowerCase().endsWith('.gguf'))
        .toList();
  }

  /// Models already available on the local server (llama.cpp / Ollama in
  /// Termux both expose an OpenAI-compatible /models endpoint).
  Future<List<String>> localModels() async {
    final base = s.localUrl.trim().replaceAll(RegExp(r'/+$'), '');
    final res = await http
        .get(Uri.parse('$base/models'))
        .timeout(const Duration(seconds: 8));
    if (res.statusCode != 200) {
      throw Exception('Could not reach the local server (${res.statusCode}).');
    }
    final data = (jsonDecode(utf8.decode(res.bodyBytes))['data'] as List?) ?? [];
    return data.map((m) => m['id'] as String).toList();
  }

  /// Chat with whichever backend is selected. Both speak the OpenAI format.
  Future<String> chat(List<Map<String, String>> messages,
      {int maxTokens = 8000}) async {
    final cloud = s.mode == AiMode.cloud;
    final base =
        cloud ? 'https://router.huggingface.co/v1' : s.localUrl.trim();
    final url = Uri.parse('${base.replaceAll(RegExp(r'/+$'), '')}/chat/completions');
    if (cloud && s.hfToken.isEmpty) {
      throw Exception('Add your Hugging Face token in Settings first.');
    }
    final res = await http
        .post(url,
            headers: {
              'Content-Type': 'application/json',
              if (cloud) 'Authorization': 'Bearer ${s.hfToken}',
            },
            body: jsonEncode({
              'model': cloud ? s.model : s.localModel,
              'messages': messages,
              'max_tokens': maxTokens,
              'temperature': 0.2,
            }))
        .timeout(const Duration(minutes: 10));
    if (res.statusCode != 200) {
      var msg = res.body;
      if (msg.length > 400) msg = msg.substring(0, 400);
      throw Exception('AI request failed (${res.statusCode}): $msg');
    }
    final body = jsonDecode(utf8.decode(res.bodyBytes));
    return body['choices'][0]['message']['content'] as String;
  }

  static const _system = '''
You are an expert Flutter developer. You write complete, compiling Flutter apps
(Dart 3, Material 3, null safety) that run on Android.

Rules:
- Output ONLY files, each in this exact format:
=== FILE: path/relative/to/project ===
<full file content>
=== END FILE ===
- Always include pubspec.yaml and lib/main.dart. Split larger apps into files under lib/.
- The pubspec name must be: {NAME}. Use environment sdk ">=3.3.0 <4.0.0".
- Only use well-known pub.dev packages (e.g. http, shared_preferences, provider,
  intl, url_launcher, path_provider, sqflite). Prefer none if not needed.
- Do NOT write android/, ios/ or test/ files; they are generated at build time.
- No placeholders, no "TODO", no explanations outside the file blocks.
''';

  /// Turn an idea into a full project.
  Future<Map<String, String>> generate(String name, String idea) async {
    final out = await chat([
      {'role': 'system', 'content': _system.replaceAll('{NAME}', name)},
      {'role': 'user', 'content': 'Build this app:\n\n$idea'},
    ]);
    final files = parseFiles(out);
    if (files.isEmpty) {
      throw Exception(
          'The model did not return any files. Try a stronger coding model.');
    }
    return files;
  }

  /// Ask for changes to an existing project. Returns only changed files.
  Future<Map<String, String>> refine(
      String name, Map<String, String> files, String request) async {
    final current = files.entries
        .map((e) => '=== FILE: ${e.key} ===\n${e.value}\n=== END FILE ===')
        .join('\n');
    final out = await chat([
      {'role': 'system', 'content': _system.replaceAll('{NAME}', name)},
      {
        'role': 'user',
        'content':
            'Current project:\n$current\n\nChange request:\n$request\n\nReturn every file you change or add, in full.'
      },
    ]);
    return parseFiles(out);
  }

  /// Ask the AI to fix a build error from the GitHub log.
  Future<Map<String, String>> fix(
      String name, Map<String, String> files, String errorLog) {
    return refine(name, files,
        'The build failed with this error. Fix it:\n\n$errorLog');
  }

  static Map<String, String> parseFiles(String text) {
    final files = <String, String>{};
    final re = RegExp(r'=== FILE: (.+?) ===\r?\n([\s\S]*?)\r?\n=== END FILE ===');
    for (final m in re.allMatches(text)) {
      var path = m.group(1)!.trim();
      var content = m.group(2)!;
      // Strip markdown fences some models add inside blocks.
      content = content.replaceFirst(RegExp(r'^```[a-zA-Z]*\r?\n'), '');
      content = content.replaceFirst(RegExp(r'\r?\n```\s*$'), '');
      if (path.startsWith('/')) path = path.substring(1);
      if (path.contains('..')) continue;
      files[path] = content;
    }
    return files;
  }
}
