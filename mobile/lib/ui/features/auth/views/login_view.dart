import 'package:flutter/cupertino.dart';
import 'package:northstar/ui/core/design_system/northstar_tokens.dart';
import 'package:northstar/ui/features/auth/view_models/auth_view_model.dart';

class LoginView extends StatefulWidget {
  const LoginView({required this.auth, super.key});

  final AuthViewModel auth;

  @override
  State<LoginView> createState() => _LoginViewState();
}

class _LoginViewState extends State<LoginView> {
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoPageScaffold(
      key: const Key('login-page'),
      child: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(NorthstarSpacing.lg),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: AnimatedBuilder(
                animation: widget.auth,
                builder: (context, _) {
                  final isLoading =
                      widget.auth.status == AuthStatus.authenticating;
                  return AutofillGroup(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Icon(
                          CupertinoIcons.sparkles,
                          size: 44,
                          color: NorthstarColors.accent.resolveFrom(context),
                        ),
                        const SizedBox(height: NorthstarSpacing.md),
                        const Text(
                          'Welcome to Northstar',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: NorthstarSpacing.xs),
                        const Text(
                          'Sign in to securely sync your assistant, tasks, and notes.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: CupertinoColors.secondaryLabel,
                            fontSize: 16,
                          ),
                        ),
                        const SizedBox(height: NorthstarSpacing.xl),
                        CupertinoTextField(
                          key: const Key('username-field'),
                          controller: _usernameController,
                          enabled: !isLoading,
                          placeholder: 'Username',
                          autofillHints: const [AutofillHints.username],
                          textInputAction: TextInputAction.next,
                          autocorrect: false,
                          padding: const EdgeInsets.all(NorthstarSpacing.md),
                        ),
                        const SizedBox(height: NorthstarSpacing.sm),
                        CupertinoTextField(
                          key: const Key('password-field'),
                          controller: _passwordController,
                          enabled: !isLoading,
                          placeholder: 'Password',
                          obscureText: true,
                          autofillHints: const [AutofillHints.password],
                          textInputAction: TextInputAction.done,
                          padding: const EdgeInsets.all(NorthstarSpacing.md),
                          onSubmitted: (_) => _submit(),
                        ),
                        if (widget.auth.errorMessage case final message?) ...[
                          const SizedBox(height: NorthstarSpacing.sm),
                          Text(
                            message,
                            key: const Key('login-error'),
                            style: const TextStyle(
                              color: CupertinoColors.systemRed,
                            ),
                          ),
                        ],
                        const SizedBox(height: NorthstarSpacing.md),
                        CupertinoButton.filled(
                          key: const Key('login-button'),
                          onPressed: isLoading ? null : _submit,
                          child: isLoading
                              ? const CupertinoActivityIndicator(
                                  color: CupertinoColors.white,
                                )
                              : const Text('Sign In'),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  void _submit() {
    widget.auth.login(
      username: _usernameController.text,
      password: _passwordController.text,
    );
  }
}
