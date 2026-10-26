import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'im_session.dart';

final imSessionProvider = ChangeNotifierProvider<ImSession>((ref) {
  throw StateError('ImSession 需要在启动时注入');
});
