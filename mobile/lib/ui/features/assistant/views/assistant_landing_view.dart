import 'dart:async';

import 'package:flutter/cupertino.dart';
import 'package:flutter_chat_core/flutter_chat_core.dart';
import 'package:flutter_chat_ui/flutter_chat_ui.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:northstar/domain/models/assistant_models.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/assistant/view_models/assistant_view_model.dart';

class AssistantLandingView extends StatefulWidget {
  const AssistantLandingView({
    super.key,
    required this.viewModel,
    this.onOpenCapture,
  });

  final AssistantViewModel viewModel;
  final VoidCallback? onOpenCapture;

  @override
  State<AssistantLandingView> createState() => _AssistantLandingViewState();
}

class _AssistantLandingViewState extends State<AssistantLandingView> {
  static const _currentUserId = 'northstar-user';
  static const _assistantUserId = 'northstar-assistant';

  final _chatController = InMemoryChatController();
  final _composerController = TextEditingController();
  final _composerFocus = FocusNode();

  AssistantViewModel get _viewModel => widget.viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel.addListener(_handleViewModelChange);
    unawaited(_viewModel.initialize());
    unawaited(_syncMessages());
  }

  @override
  void didUpdateWidget(covariant AssistantLandingView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.viewModel != widget.viewModel) {
      oldWidget.viewModel.removeListener(_handleViewModelChange);
      _viewModel.addListener(_handleViewModelChange);
      unawaited(_viewModel.initialize());
      unawaited(_syncMessages());
    }
  }

  void _handleViewModelChange() {
    if (!mounted) {
      return;
    }
    setState(() {});
    unawaited(_syncMessages());
  }

  Future<void> _syncMessages() async {
    final next = _viewModel.messages
        .map(_toChatMessage)
        .toList(growable: false);
    final current = _chatController.messages;
    final sameIdentity =
        current.length == next.length &&
        List.generate(
          next.length,
          (index) => current[index].id == next[index].id,
        ).every((matches) => matches);
    if (!sameIdentity) {
      await _chatController.setMessages(next, animated: false);
      return;
    }
    for (var index = 0; index < next.length; index += 1) {
      if (current[index] != next[index]) {
        await _chatController.updateMessage(current[index], next[index]);
      }
    }
  }

  Message _toChatMessage(AssistantMessage message) {
    return Message.text(
      id: message.id,
      authorId: message.role == AssistantRole.user
          ? _currentUserId
          : _assistantUserId,
      text: message.text,
      metadata: {
        'status': message.status.name,
        'error': message.errorMessage,
        'tools': [
          for (final tool in message.tools)
            {'name': tool.name, 'status': tool.status.name},
        ],
      },
    );
  }

  Future<void> _submit() async {
    final prompt = _composerController.text;
    if (prompt.trim().isEmpty || _viewModel.isSending) {
      return;
    }
    _composerController.clear();
    _composerFocus.requestFocus();
    await _viewModel.send(prompt);
  }

  Future<void> _showModelPicker() async {
    if (_viewModel.models.isEmpty || _viewModel.isSending) return;
    await showCupertinoModalPopup<void>(
      context: context,
      builder: (modalContext) => CupertinoActionSheet(
        title: const Text('Assistant model'),
        message: Text('Gateway: ${_viewModel.modelSelection?.gatewayId ?? ''}'),
        actions: [
          for (final model in _viewModel.models)
            CupertinoActionSheetAction(
              isDefaultAction: model.id == _viewModel.modelSelection?.modelId,
              onPressed: () {
                Navigator.of(modalContext).pop();
                unawaited(_viewModel.selectModel(model.id));
              },
              child: Text(model.displayName),
            ),
        ],
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.of(modalContext).pop(),
          child: const Text('Cancel'),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _viewModel.removeListener(_handleViewModelChange);
    _chatController.dispose();
    _composerController.dispose();
    _composerFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('assistant-page'),
      navigationBar: CupertinoNavigationBar(
        middle: const Text('Assistant'),
        leading: CupertinoButton(
          key: const Key('assistant-history-button'),
          padding: EdgeInsets.zero,
          onPressed: _showConversationHistory,
          child: const Icon(
            CupertinoIcons.clock,
            semanticLabel: 'Conversation history',
          ),
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (widget.onOpenCapture != null)
              CupertinoButton(
                key: const Key('assistant-capture-button'),
                padding: EdgeInsets.zero,
                onPressed: widget.onOpenCapture,
                child: const Icon(
                  CupertinoIcons.plus_circle,
                  semanticLabel: 'Capture',
                ),
              ),
            CupertinoButton(
              key: const Key('assistant-new-chat-button'),
              padding: EdgeInsets.zero,
              onPressed: _viewModel.startNewConversation,
              child: const Icon(
                CupertinoIcons.square_pencil,
                semanticLabel: 'New conversation',
              ),
            ),
          ],
        ),
      ),
      child: SafeArea(
        bottom: false,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final expanded = constraints.maxWidth >= 840;
            if (expanded) {
              return Row(
                children: [
                  SizedBox(
                    width: NorthstarLayout.sidebarWidth,
                    child: _ConversationSidebar(
                      viewModel: _viewModel,
                      onSelect: _viewModel.selectConversation,
                    ),
                  ),
                  Container(
                    width: 0.5,
                    color: NorthstarColors.separator.resolveFrom(context),
                  ),
                  Expanded(child: _buildChat(context)),
                ],
              );
            }
            return _buildChat(context);
          },
        ),
      ),
    );
  }

  Widget _buildChat(BuildContext context) {
    if (_viewModel.isLoading && _viewModel.messages.isEmpty) {
      return const Center(
        child: CupertinoActivityIndicator(
          key: Key('assistant-loading-indicator'),
          radius: 14,
        ),
      );
    }
    if (_viewModel.loadError case final error?) {
      return _AssistantLoadError(message: error, onRetry: _viewModel.reload);
    }

    return Column(
      children: [
        Expanded(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: NorthstarLayout.readableContentWidth,
            ),
            child: Chat(
              chatController: _chatController,
              currentUserId: _currentUserId,
              resolveUser: (id) async => User(
                id: id,
                name: id == _currentUserId ? 'You' : 'Northstar',
              ),
              backgroundColor: NorthstarColors.background.resolveFrom(context),
              builders: Builders(
                composerBuilder: (_) => const SizedBox.shrink(),
                emptyChatListBuilder: (_) =>
                    _AssistantEmptyState(onSuggestion: _viewModel.send),
                textMessageBuilder: _buildTextMessage,
              ),
            ),
          ),
        ),
        _AssistantComposer(
          controller: _composerController,
          focusNode: _composerFocus,
          isSending: _viewModel.isSending,
          onSend: _submit,
          onStop: _viewModel.stop,
          modelLabel: _viewModel.models
              .where((model) => model.id == _viewModel.modelSelection?.modelId)
              .map((model) => model.displayName)
              .firstOrNull,
          onSelectModel: _viewModel.models.isEmpty ? null : _showModelPicker,
        ),
      ],
    );
  }

  Widget _buildTextMessage(
    BuildContext context,
    TextMessage message,
    int index, {
    required bool isSentByMe,
    MessageGroupStatus? groupStatus,
  }) {
    final metadata = message.metadata ?? const <String, dynamic>{};
    final status = metadata['status'] as String?;
    final error = metadata['error'] as String?;
    final rawTools = metadata['tools'];
    final tools = rawTools is List
        ? rawTools.whereType<Map>().toList()
        : const [];
    final isWaiting =
        !isSentByMe &&
        status == AssistantMessageStatus.streaming.name &&
        message.text.trim().isEmpty &&
        tools.isEmpty;

    return Semantics(
      label: isSentByMe ? 'Your message' : 'Northstar response',
      child: Container(
        key: isWaiting ? const Key('assistant-waiting-indicator') : null,
        constraints: BoxConstraints(
          maxWidth: isSentByMe ? 560 : NorthstarLayout.readableContentWidth,
        ),
        padding: EdgeInsets.all(
          isSentByMe ? NorthstarSpacing.sm : NorthstarSpacing.md,
        ),
        decoration: BoxDecoration(
          color: isSentByMe
              ? NorthstarColors.accent.resolveFrom(context)
              : NorthstarColors.elevatedSurface.resolveFrom(context),
          borderRadius: BorderRadius.circular(NorthstarRadii.lg),
          border: isSentByMe
              ? null
              : Border.all(
                  color: NorthstarColors.separator.resolveFrom(context),
                  width: 0.5,
                ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (isWaiting)
              const Row(
                children: [
                  CupertinoActivityIndicator(radius: 8),
                  SizedBox(width: NorthstarSpacing.sm),
                  Flexible(child: Text('Northstar is thinking…')),
                ],
              ),
            if (message.text.trim().isNotEmpty)
              isSentByMe
                  ? Text(
                      message.text,
                      style: NorthstarTextStyles.body(
                        context,
                      ).copyWith(color: CupertinoColors.white),
                    )
                  : MarkdownBody(
                      data: message.text,
                      selectable: true,
                      styleSheet: _cupertinoMarkdownStyle(context),
                      styleSheetTheme: MarkdownStyleSheetBaseTheme.cupertino,
                    ),
            for (final tool in tools)
              _AssistantToolRow(
                name: tool['name']?.toString() ?? 'Northstar tool',
                status: tool['status']?.toString() ?? 'preparing',
              ),
            if (error != null) ...[
              if (message.text.trim().isNotEmpty || tools.isNotEmpty)
                const SizedBox(height: NorthstarSpacing.sm),
              Text(
                error,
                key: const Key('assistant-message-error'),
                style: NorthstarTextStyles.caption(context).copyWith(
                  color: NorthstarColors.destructive.resolveFrom(context),
                ),
              ),
              const SizedBox(height: NorthstarSpacing.xs),
              CupertinoButton(
                key: const Key('assistant-retry-button'),
                padding: EdgeInsets.zero,
                onPressed: _viewModel.canRetry ? _viewModel.retry : null,
                child: const Text('Try again'),
              ),
            ],
            if (status == AssistantMessageStatus.stopped.name) ...[
              const SizedBox(height: NorthstarSpacing.xs),
              Text(
                'Response stopped',
                style: NorthstarTextStyles.caption(context),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _showConversationHistory() {
    return showCupertinoModalPopup<void>(
      context: context,
      builder: (modalContext) => CupertinoPopupSurface(
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: MediaQuery.sizeOf(modalContext).height * 0.62,
            child: _ConversationSidebar(
              viewModel: _viewModel,
              onSelect: (id) async {
                Navigator.of(modalContext).pop();
                await _viewModel.selectConversation(id);
              },
            ),
          ),
        ),
      ),
    );
  }
}

MarkdownStyleSheet _cupertinoMarkdownStyle(BuildContext context) {
  final theme = CupertinoTheme.of(context);
  final foreground = NorthstarColors.primaryText.resolveFrom(context);
  final secondary = NorthstarColors.secondaryText.resolveFrom(context);
  final accent = NorthstarColors.accent.resolveFrom(context);
  final base = MarkdownStyleSheet.fromCupertinoTheme(theme);
  final body = NorthstarTextStyles.body(context).copyWith(color: foreground);

  TextStyle? readable(TextStyle? style) => style?.copyWith(color: foreground);

  return base.copyWith(
    a: base.a?.copyWith(color: accent),
    p: body,
    code: readable(base.code),
    h1: readable(base.h1),
    h2: readable(base.h2),
    h3: readable(base.h3),
    h4: readable(base.h4),
    h5: readable(base.h5),
    h6: readable(base.h6),
    em: readable(base.em),
    strong: readable(base.strong),
    del: readable(base.del),
    blockquote: base.blockquote?.copyWith(color: secondary),
    listBullet: readable(base.listBullet),
    tableHead: readable(base.tableHead),
    tableBody: readable(base.tableBody),
  );
}

class _AssistantComposer extends StatelessWidget {
  const _AssistantComposer({
    required this.controller,
    required this.focusNode,
    required this.isSending,
    required this.onSend,
    required this.onStop,
    required this.modelLabel,
    required this.onSelectModel,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool isSending;
  final VoidCallback onSend;
  final VoidCallback onStop;
  final String? modelLabel;
  final VoidCallback? onSelectModel;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: NorthstarColors.elevatedSurface.resolveFrom(context),
        border: Border(
          top: BorderSide(
            color: NorthstarColors.separator.resolveFrom(context),
            width: 0.5,
          ),
        ),
      ),
      padding: EdgeInsets.fromLTRB(
        NorthstarSpacing.md,
        NorthstarSpacing.sm,
        NorthstarSpacing.md,
        MediaQuery.paddingOf(context).bottom + NorthstarSpacing.xs,
      ),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: NorthstarLayout.readableContentWidth,
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (modelLabel != null) ...[
                Align(
                  alignment: Alignment.centerLeft,
                  child: CupertinoButton(
                    key: const Key('assistant-model-picker'),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 2,
                    ),
                    minimumSize: const Size(0, 30),
                    onPressed: isSending ? null : onSelectModel,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(CupertinoIcons.sparkles, size: 15),
                        const SizedBox(width: NorthstarSpacing.xs),
                        Text(
                          modelLabel!,
                          style: NorthstarTextStyles.caption(context),
                        ),
                        const SizedBox(width: 2),
                        const Icon(CupertinoIcons.chevron_down, size: 12),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: NorthstarSpacing.xxs),
              ],
              CupertinoTextField(
                key: const Key('assistant-composer'),
                controller: controller,
                focusNode: focusNode,
                enabled: !isSending,
                placeholder: 'Ask Northstar…',
                minLines: 1,
                maxLines: 5,
                padding: const EdgeInsets.fromLTRB(16, 12, 48, 12),
                suffix: Padding(
                  padding: const EdgeInsets.only(right: NorthstarSpacing.xs),
                  child: CupertinoButton(
                    key: Key(
                      isSending
                          ? 'assistant-stop-button'
                          : 'assistant-send-button',
                    ),
                    padding: EdgeInsets.zero,
                    minimumSize: const Size.square(36),
                    onPressed: isSending ? onStop : onSend,
                    child: Icon(
                      isSending
                          ? CupertinoIcons.stop_circle_fill
                          : CupertinoIcons.arrow_up_circle_fill,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: NorthstarSpacing.xxs),
              Text(
                'AI-generated responses can be wrong. Check important details.',
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.caption(context),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AssistantEmptyState extends StatelessWidget {
  const _AssistantEmptyState({required this.onSuggestion});

  final ValueChanged<String> onSuggestion;

  static const suggestions = [
    'What should I focus on today?',
    'Summarize my recent notes',
    'Help me plan this week',
  ];

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(NorthstarSpacing.lg),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 520),
          child: Column(
            children: [
              const Icon(CupertinoIcons.sparkles, size: 34),
              const SizedBox(height: NorthstarSpacing.md),
              Text(
                'How can I help?',
                style: NorthstarTextStyles.sectionTitle(context),
              ),
              const SizedBox(height: NorthstarSpacing.xs),
              Text(
                'Ask about your notes, plans, tasks, or finances.',
                textAlign: TextAlign.center,
                style: NorthstarTextStyles.body(context).copyWith(
                  color: NorthstarColors.secondaryText.resolveFrom(context),
                ),
              ),
              const SizedBox(height: NorthstarSpacing.lg),
              for (final suggestion in suggestions)
                Padding(
                  padding: const EdgeInsets.only(bottom: NorthstarSpacing.xs),
                  child: SizedBox(
                    width: double.infinity,
                    child: CupertinoButton.tinted(
                      onPressed: () => onSuggestion(suggestion),
                      child: Text(suggestion),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AssistantToolRow extends StatelessWidget {
  const _AssistantToolRow({required this.name, required this.status});

  final String name;
  final String status;

  @override
  Widget build(BuildContext context) {
    final complete = status == AssistantToolStatus.complete.name;
    final failed = status == AssistantToolStatus.failed.name;
    return Padding(
      padding: const EdgeInsets.only(top: NorthstarSpacing.sm),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (!complete && !failed)
            const CupertinoActivityIndicator(radius: 7)
          else
            Icon(
              failed
                  ? CupertinoIcons.exclamationmark_circle_fill
                  : CupertinoIcons.check_mark_circled_solid,
              size: 16,
              color: failed
                  ? NorthstarColors.destructive.resolveFrom(context)
                  : NorthstarColors.positive.resolveFrom(context),
            ),
          const SizedBox(width: NorthstarSpacing.xs),
          Flexible(
            child: Text(
              complete ? '$name complete' : name,
              style: NorthstarTextStyles.caption(context),
            ),
          ),
        ],
      ),
    );
  }
}

class _ConversationSidebar extends StatelessWidget {
  const _ConversationSidebar({required this.viewModel, required this.onSelect});

  final AssistantViewModel viewModel;
  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    return Container(
      key: const Key('assistant-conversation-sidebar'),
      color: NorthstarColors.elevatedSurface.resolveFrom(context),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(NorthstarSpacing.md),
            child: Text(
              'Conversations',
              style: NorthstarTextStyles.sectionTitle(context),
            ),
          ),
          Expanded(
            child: viewModel.conversations.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(NorthstarSpacing.md),
                      child: Text(
                        'No previous conversations',
                        textAlign: TextAlign.center,
                        style: NorthstarTextStyles.caption(context),
                      ),
                    ),
                  )
                : ListView.builder(
                    itemCount: viewModel.conversations.length,
                    itemBuilder: (context, index) {
                      final conversation = viewModel.conversations[index];
                      final selected =
                          conversation.id == viewModel.conversationId;
                      return CupertinoButton(
                        padding: const EdgeInsets.symmetric(
                          horizontal: NorthstarSpacing.md,
                          vertical: NorthstarSpacing.sm,
                        ),
                        onPressed: () => onSelect(conversation.id),
                        child: Align(
                          alignment: Alignment.centerLeft,
                          child: Text(
                            conversation.title,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: NorthstarTextStyles.body(context).copyWith(
                              fontWeight: selected ? FontWeight.w700 : null,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class _AssistantLoadError extends StatelessWidget {
  const _AssistantLoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(NorthstarSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(CupertinoIcons.exclamationmark_triangle, size: 32),
            const SizedBox(height: NorthstarSpacing.sm),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: NorthstarSpacing.md),
            CupertinoButton.filled(
              onPressed: onRetry,
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}
