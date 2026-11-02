import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Where the AI runs.
enum AiMode { cloud, local }

class Settings {
  String hfToken;
  String githubToken;
  String githubRepo; // "owner/repo"
  String model; // Hugging Face model id, e.g. Qwen/Qwen2.5-Coder-32B-Instruct
  AiMode mode;
  String localUrl; // OpenAI-compatible server on the phone (llama.cpp / Ollama in Termux)
  String localModel;

  Settings({
    this.hfToken = '',
    this.githubToken = '',
    this.githubRepo = '',
    this.model = 'Qwen/Qwen2.5-Coder-32B-Instruct',
    this.mode = AiMode.cloud,
    this.localUrl = 'http://127.0.0.1:8080/v1',
    this.localModel = 'local',
  });

  static Future<Settings> load() async {
    final p = await SharedPreferences.getInstance();
    return Settings(
      hfToken: p.getString('hfToken') ?? '',
      githubToken: p.getString('githubToken') ?? '',
      githubRepo: p.getString('githubRepo') ?? '',
      model: p.getString('model') ?? 'Qwen/Qwen2.5-Coder-32B-Instruct',
      mode: AiMode.values[p.getInt('mode') ?? 0],
      localUrl: p.getString('localUrl') ?? 'http://127.0.0.1:8080/v1',
      localModel: p.getString('localModel') ?? 'local',
    );
  }

  Future<void> save() async {
    final p = await SharedPreferences.getInstance();
    await p.setString('hfToken', hfToken);
    await p.setString('githubToken', githubToken);
    await p.setString('githubRepo', githubRepo);
    await p.setString('model', model);
    await p.setInt('mode', mode.index);
    await p.setString('localUrl', localUrl);
    await p.setString('localModel', localModel);
  }
}

/// A generated app: an idea plus the files the AI wrote for it.
class Project {
  String id;
  String name; // dart package name, e.g. my_todo_app
  String idea;
  Map<String, String> files; // path -> content
  DateTime updated;
  String? lastReleaseUrl;

  Project({
    required this.id,
    required this.name,
    required this.idea,
    required this.files,
    required this.updated,
    this.lastReleaseUrl,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'idea': idea,
        'files': files,
        'updated': updated.toIso8601String(),
        'lastReleaseUrl': lastReleaseUrl,
      };

  static Project fromJson(Map<String, dynamic> j) => Project(
        id: j['id'],
        name: j['name'],
        idea: j['idea'],
        files: Map<String, String>.from(j['files']),
        updated: DateTime.parse(j['updated']),
        lastReleaseUrl: j['lastReleaseUrl'],
      );

  String get branch => 'app/$name';
}

class ProjectStore {
  static Future<List<Project>> all() async {
    final p = await SharedPreferences.getInstance();
    final raw = p.getString('projects');
    if (raw == null) return [];
    final list = (jsonDecode(raw) as List)
        .map((e) => Project.fromJson(e as Map<String, dynamic>))
        .toList();
    list.sort((a, b) => b.updated.compareTo(a.updated));
    return list;
  }

  static Future<void> _write(List<Project> list) async {
    final p = await SharedPreferences.getInstance();
    await p.setString(
        'projects', jsonEncode(list.map((e) => e.toJson()).toList()));
  }

  static Future<void> save(Project project) async {
    final list = await all();
    list.removeWhere((e) => e.id == project.id);
    project.updated = DateTime.now();
    list.add(project);
    await _write(list);
  }

  static Future<void> delete(String id) async {
    final list = await all();
    list.removeWhere((e) => e.id == id);
    await _write(list);
  }
}
