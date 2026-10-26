import 'package:permission_handler/permission_handler.dart';

enum AppPermission { notifications, microphone, camera, photos }

abstract class PermissionGateway {
  Future<bool> ensure(AppPermission permission);
}

class GrantingPermissions implements PermissionGateway {
  final granted = <AppPermission>{};

  @override
  Future<bool> ensure(AppPermission permission) async {
    granted.add(permission);
    return true;
  }
}

class PlatformPermissionGateway implements PermissionGateway {
  @override
  Future<bool> ensure(AppPermission permission) async {
    final status = await _permission(permission).request();
    return status.isGranted || status.isLimited;
  }

  Permission _permission(AppPermission permission) {
    switch (permission) {
      case AppPermission.notifications:
        return Permission.notification;
      case AppPermission.microphone:
        return Permission.microphone;
      case AppPermission.camera:
        return Permission.camera;
      case AppPermission.photos:
        return Permission.photos;
    }
  }
}

class PermissionService {
  PermissionService({PermissionGateway? gateway}) : _gateway = gateway ?? PlatformPermissionGateway();

  final PermissionGateway _gateway;

  Future<bool> ensureNotifications() => _gateway.ensure(AppPermission.notifications);
  Future<bool> ensureMicrophone() => _gateway.ensure(AppPermission.microphone);
  Future<bool> ensureCamera() => _gateway.ensure(AppPermission.camera);
  Future<bool> ensureMedia() => _gateway.ensure(AppPermission.photos);

  Future<bool> ensureCall({required bool video}) async {
    final mic = await ensureMicrophone();
    if (!video) return mic;
    final camera = await ensureCamera();
    return mic && camera;
  }
}
