class NoteDetail {
  const NoteDetail({
    required this.id,
    required this.title,
    required this.slug,
    required this.folderPath,
    required this.contentMarkdown,
    required this.tags,
    required this.status,
    required this.updatedAt,
  });

  final String id;
  final String title;
  final String slug;
  final String folderPath;
  final String contentMarkdown;
  final List<String> tags;
  final String status;
  final DateTime updatedAt;
}
