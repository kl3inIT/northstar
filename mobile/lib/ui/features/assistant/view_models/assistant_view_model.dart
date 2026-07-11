import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:northstar/data/repositories/assistant_repository.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:uuid/uuid.dart';

class AssistantViewModel extends ChangeNotifier {
  AssistantViewModel(this._repository, [this._uuid = const Uuid()]);

  final AssistantRepository _repository;
  final Uuid _uuid;

  final List<AssistantConversation> _conversations = [];
  final List<AssistantMessage> _messages = [];
  StreamSubscription<AssistantTurnEvent>? _turnSubscription;
  Completer<void>? _turnCompleter;
  String? _conversationId;
  String? _lastPrompt;
  String? _loadError;
  bool _initialized = false;
  bool _isLoading = false;
  bool _isSending = false;
  AssistantModelSelection? _modelSelection;
  List<AssistantModelOption> _models = const [];

  List<AssistantConversation> get conversations =>
      List.unmodifiable(_conversations);
  List<AssistantMessage> get messages => List.unmodifiable(_messages);
  String? get conversationId => _conversationId;
  String? get loadError => _loadError;
  bool get isLoading => _isLoading;
  bool get isSending => _isSending;
  bool get canRetry => !_isSending && _lastPrompt != null;
  AssistantModelSelection? get modelSelection => _modelSelection;
  List<AssistantModelOption> get models => List.unmodifiable(_models);

  Future<void> initialize() async {
    if (_initialized || _isLoading) {
      return;
    }
    _isLoading = true;
    _loadError = null;
    notifyListeners();
    try {
      final conversations = await _repository.listConversations();
      _conversations
        ..clear()
        ..addAll(conversations);
      if (conversations.isEmpty) {
        _conversationId = _uuid.v4();
        _messages.clear();
      } else {
        _conversationId = conversations.first.id;
        await _loadHistory(conversations.first.id);
      }
      await _loadModels(_conversationId!);
      _initialized = true;
    } on Object catch (error) {
      _loadError = _messageFor(error);
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> reload() async {
    _initialized = false;
    await initialize();
  }

  Future<void> startNewConversation() async {
    await stop();
    _conversationId = _uuid.v4();
    _messages.clear();
    _lastPrompt = null;
    _loadError = null;
    await _loadModels(_conversationId!);
    notifyListeners();
  }

  Future<void> selectConversation(String id) async {
    if (id == _conversationId || _isLoading) {
      return;
    }
    await stop();
    _conversationId = id;
    _isLoading = true;
    _loadError = null;
    notifyListeners();
    try {
      await _loadHistory(id);
      await _loadModels(id);
    } on Object catch (error) {
      _loadError = _messageFor(error);
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> selectModel(String modelId) async {
    final repository = _repository is AssistantModelRepository
        ? _repository as AssistantModelRepository
        : null;
    final conversationId = _conversationId;
    final current = _modelSelection;
    if (repository == null ||
        conversationId == null ||
        current == null ||
        _isSending) {
      return;
    }
    try {
      _modelSelection = await repository.updateConversationModel(
        conversationId,
        AssistantModelSelection(gatewayId: current.gatewayId, modelId: modelId),
      );
      notifyListeners();
    } on Object catch (error) {
      _loadError = _messageFor(error);
      notifyListeners();
    }
  }

  Future<void> _loadModels(String conversationId) async {
    final repository = _repository is AssistantModelRepository
        ? _repository as AssistantModelRepository
        : null;
    if (repository == null) return;
    final selection = await repository.conversationModel(conversationId);
    final models = await repository.models(selection.gatewayId);
    _modelSelection = selection;
    _models = models;
  }

  Future<void> send(String rawPrompt) async {
    final prompt = rawPrompt.trim();
    if (prompt.isEmpty || _isSending) {
      return;
    }
    final conversationId = _conversationId ??= _uuid.v4();
    final userMessage = AssistantMessage(
      id: _uuid.v4(),
      role: AssistantRole.user,
      text: prompt,
    );
    final assistantMessage = AssistantMessage(
      id: _uuid.v4(),
      role: AssistantRole.assistant,
      text: '',
      status: AssistantMessageStatus.streaming,
    );
    _messages.addAll([userMessage, assistantMessage]);
    _lastPrompt = prompt;
    _isSending = true;
    _loadError = null;
    notifyListeners();

    final completer = Completer<void>();
    _turnCompleter = completer;
    var failed = false;
    _turnSubscription = _repository
        .streamTurn(conversationId: conversationId, message: prompt)
        .listen(
          (event) => _applyEvent(assistantMessage.id, event),
          onError: (Object error, StackTrace stackTrace) {
            failed = true;
            _markFailed(assistantMessage.id, _messageFor(error));
            _completeTurn(completer);
          },
          onDone: () {
            if (!failed) {
              _markComplete(assistantMessage.id);
            }
            _completeTurn(completer);
          },
          cancelOnError: true,
        );
    await completer.future;
  }

  Future<void> retry() async {
    final prompt = _lastPrompt;
    if (prompt == null || _isSending) {
      return;
    }
    if (_messages.length >= 2 &&
        _messages.last.status == AssistantMessageStatus.failed &&
        _messages[_messages.length - 2].role == AssistantRole.user &&
        _messages[_messages.length - 2].text == prompt) {
      _messages.removeRange(_messages.length - 2, _messages.length);
    }
    await send(prompt);
  }

  Future<void> stop() async {
    final subscription = _turnSubscription;
    if (subscription == null) {
      return;
    }
    _turnSubscription = null;
    await subscription.cancel();
    final index = _messages.lastIndexWhere(
      (message) => message.status == AssistantMessageStatus.streaming,
    );
    if (index >= 0) {
      _messages[index] = _messages[index].copyWith(
        status: AssistantMessageStatus.stopped,
      );
    }
    _isSending = false;
    final completer = _turnCompleter;
    _turnCompleter = null;
    if (completer != null && !completer.isCompleted) {
      completer.complete();
    }
    notifyListeners();
  }

  Future<void> _loadHistory(String id) async {
    final history = await _repository.history(id);
    _messages
      ..clear()
      ..addAll(history);
    _lastPrompt = null;
  }

  void _applyEvent(String messageId, AssistantTurnEvent event) {
    final index = _messages.indexWhere((message) => message.id == messageId);
    if (index < 0) {
      return;
    }
    final current = _messages[index];
    switch (event) {
      case AssistantTextDeltaEvent(:final delta):
        _messages[index] = current.copyWith(text: current.text + delta);
      case AssistantToolEvent(:final id, :final name, :final status):
        final tools = current.tools.toList();
        final toolIndex = tools.indexWhere((tool) => tool.id == id);
        if (toolIndex < 0) {
          tools.add(
            AssistantToolActivity(
              id: id,
              name: name ?? 'Northstar tool',
              status: status,
            ),
          );
        } else {
          tools[toolIndex] = tools[toolIndex].copyWith(status: status);
        }
        _messages[index] = current.copyWith(tools: List.unmodifiable(tools));
      case AssistantTurnFinishedEvent():
        _messages[index] = current.copyWith(
          status: AssistantMessageStatus.complete,
        );
    }
    notifyListeners();
  }

  void _markComplete(String id) {
    final index = _messages.indexWhere((message) => message.id == id);
    if (index >= 0 &&
        _messages[index].status == AssistantMessageStatus.streaming) {
      _messages[index] = _messages[index].copyWith(
        status: AssistantMessageStatus.complete,
      );
      notifyListeners();
    }
  }

  void _markFailed(String id, String message) {
    final index = _messages.indexWhere((item) => item.id == id);
    if (index >= 0) {
      final tools = _messages[index].tools
          .map((tool) {
            if (tool.status == AssistantToolStatus.complete) {
              return tool;
            }
            return tool.copyWith(status: AssistantToolStatus.failed);
          })
          .toList(growable: false);
      _messages[index] = _messages[index].copyWith(
        tools: tools,
        status: AssistantMessageStatus.failed,
        errorMessage: message,
      );
    }
    notifyListeners();
  }

  void _completeTurn(Completer<void> completer) {
    if (!identical(_turnCompleter, completer)) {
      return;
    }
    _turnSubscription = null;
    _turnCompleter = null;
    _isSending = false;
    if (!completer.isCompleted) {
      completer.complete();
    }
    notifyListeners();
  }

  String _messageFor(Object error) {
    final text = error.toString().trim();
    return text.isEmpty ? 'Northstar could not complete that request.' : text;
  }

  @override
  void dispose() {
    unawaited(_turnSubscription?.cancel());
    super.dispose();
  }
}
