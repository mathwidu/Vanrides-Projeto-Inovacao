import 'package:vanrides_frontend_dev/features/admin/data/admin_vinculo_portal_service.dart';
import 'package:vanrides_frontend_dev/features/admin/domain/admin_company_link_flow_models.dart';

class FakePassengerOnboardingGateway
    implements AdminPassengerOnboardingGateway {
  FakePassengerOnboardingGateway({
    List<AdminAccessRequestRecord>? requests,
    List<AdminInviteCodeRecord>? inviteCodes,
  }) : _requests = requests ?? _defaultRequests,
       _inviteCodes = inviteCodes ?? _defaultInviteCodes;

  factory FakePassengerOnboardingGateway.withMany({required int count}) {
    return FakePassengerOnboardingGateway(
      requests: List.generate(
        count,
        (index) => AdminAccessRequestRecord(
          id: 31 + index,
          passageiroId: 91 + index,
          passageiroNome: 'Aluno Solicitante ${index + 1}',
          passageiroEmail: 'solicitante${index + 1}@vanrides.dev',
          companyName: 'Portal Empresa',
          source: 'REQUEST',
          emailVerified: index.isEven,
        ),
      ),
      inviteCodes: List.generate(
        count,
        (index) => AdminInviteCodeRecord(
          id: 44 + index,
          code: 'VAN-PAX-${(index + 1).toString().padLeft(3, '0')}',
          status: index.isEven ? 'ACTIVE' : 'USED_UP',
          useCount: index.isEven ? 0 : 1,
          maxUses: 1,
        ),
      ),
    );
  }

  final List<AdminAccessRequestRecord> _requests;
  final List<AdminInviteCodeRecord> _inviteCodes;
  int? approvedRequestId;
  List<int>? approvedServicoIds;
  int? rejectedRequestId;
  int? createdMaxUses;
  int? openedInviteId;
  int? cancelledInviteId;
  int? regeneratedInviteId;
  var fetchAccessRequestsCalls = 0;

  @override
  Future<List<AdminAccessRequestRecord>> fetchAccessRequests() async {
    fetchAccessRequestsCalls++;
    return _requests;
  }

  @override
  Future<AdminAccessRequestDetailRecord> fetchAccessRequestDetail(
    int id,
  ) async {
    return const AdminAccessRequestDetailRecord(
      id: 31,
      passageiroId: 91,
      passageiroNome: 'Aluno Solicitante',
      passageiroEmail: 'solicitante@vanrides.dev',
      passageiroTelefone: '51999999995',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'PENDING',
      emailVerified: true,
    );
  }

  @override
  Future<AdminAccessRequestDetailRecord> approveAccessRequest({
    required int requestId,
    int? motoristaId,
    String? reason,
    List<int>? servicoIds,
  }) async {
    approvedRequestId = requestId;
    approvedServicoIds = servicoIds;
    return const AdminAccessRequestDetailRecord(
      id: 31,
      passageiroId: 91,
      passageiroNome: 'Aluno Solicitante',
      passageiroEmail: 'solicitante@vanrides.dev',
      passageiroTelefone: '51999999995',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'APPROVED',
      emailVerified: true,
    );
  }

  @override
  Future<AdminAccessRequestDetailRecord> rejectAccessRequest({
    required int requestId,
    String? reason,
  }) async {
    rejectedRequestId = requestId;
    return const AdminAccessRequestDetailRecord(
      id: 31,
      passageiroId: 91,
      passageiroNome: 'Aluno Solicitante',
      passageiroEmail: 'solicitante@vanrides.dev',
      passageiroTelefone: '51999999995',
      companyName: 'Portal Empresa',
      source: 'REQUEST',
      status: 'REJECTED',
      emailVerified: true,
    );
  }

  @override
  Future<List<AdminInviteCodeRecord>> fetchInviteCodes() async => _inviteCodes;

  @override
  Future<AdminInviteCodeDetailRecord> fetchInviteCodeDetail(int id) async {
    openedInviteId = id;
    final invite = _inviteCodes.firstWhere(
      (item) => item.id == id,
      orElse: () => _defaultInviteCodes.first,
    );
    return AdminInviteCodeDetailRecord(
      id: invite.id,
      code: invite.code,
      status: invite.status,
      useCount: invite.useCount,
      maxUses: invite.maxUses,
      expiresAt: invite.expiresAt,
      createdAt: invite.createdAt,
    );
  }

  @override
  Future<AdminInviteCodeDetailRecord> createInviteCode({
    DateTime? expiresAt,
    int? maxUses,
  }) async {
    createdMaxUses = maxUses;
    return const AdminInviteCodeDetailRecord(
      id: 46,
      code: 'VAN-PAX-NOVO',
      status: 'ACTIVE',
      useCount: 0,
      maxUses: 1,
    );
  }

  @override
  Future<AdminInviteCodeDetailRecord> cancelInviteCode(int id) async {
    cancelledInviteId = id;
    return AdminInviteCodeDetailRecord(
      id: id,
      code: 'VAN-PAX-2026',
      status: 'CANCELLED',
      useCount: 0,
      maxUses: 1,
    );
  }

  @override
  Future<AdminInviteCodeDetailRecord> regenerateInviteCode(int id) async {
    regeneratedInviteId = id;
    return const AdminInviteCodeDetailRecord(
      id: 47,
      code: 'VAN-PAX-REGEN',
      status: 'ACTIVE',
      useCount: 0,
      maxUses: 1,
    );
  }
}

const _defaultRequests = [
  AdminAccessRequestRecord(
    id: 31,
    passageiroId: 91,
    passageiroNome: 'Aluno Solicitante',
    passageiroEmail: 'solicitante@vanrides.dev',
    companyName: 'Portal Empresa',
    source: 'REQUEST',
    emailVerified: true,
  ),
];

const _defaultInviteCodes = [
  AdminInviteCodeRecord(
    id: 44,
    code: 'VAN-PAX-2026',
    status: 'ACTIVE',
    useCount: 0,
    maxUses: 1,
  ),
  AdminInviteCodeRecord(
    id: 45,
    code: 'VAN-PAX-USADO',
    status: 'USED_UP',
    useCount: 1,
    maxUses: 1,
  ),
];
