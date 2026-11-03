final class PassageiroStrings {
  const PassageiroStrings();

  final String appName = 'VanRides';
  final String problemasComuns = 'Problemas comuns';

  final String contaAtivaBadge = 'CONTA ATIVA';
  final String entrarEmUmaEmpresaTitulo = 'Entrar em uma empresa';
  final String entrarEmUmaEmpresaSubtitulo =
      'Sua conta já está pronta e verificada. Agora precisamos conectar seu perfil à empresa correta para liberar a operação.';
  final String oQueAconteceAgoraTitulo = 'O que acontece agora';
  final String oQueAconteceAgoraDescricao =
      'Você pode solicitar acesso pesquisando sua empresa ou validar um código de convite fornecido pelo time administrativo.';
  final String vincularEmpresaTitulo = 'Vincular a uma empresa';
  final String vincularEmpresaDescricao =
      'Pesquise a organização e envie sua solicitação para análise do portal.';
  final String jaTenhoCodigoTitulo = 'Já tenho um código';
  final String jaTenhoCodigoDescricao =
      'Valide o código corporativo para concluir o vínculo de forma imediata.';
  final String atualizarMeuStatus = 'Atualizar meu status';

  final String buscaCorporativaBadge = 'BUSCA CORPORATIVA';
  final String buscarEmpresaTitulo = 'Buscar empresa';
  final String buscarEmpresaDescricao =
      'Encontre a empresa pelo nome e revise os dados antes de encaminhar sua solicitação de acesso.';
  final String pesquisarEmpresaHint = 'Pesquisar empresa por nome';
  final String buscar = 'Buscar';
  final String naoFoiPossivelCarregar = 'Não foi possível carregar';
  final String nenhumaEmpresaEncontrada = 'Nenhuma empresa encontrada';
  final String nenhumaEmpresaEncontradaDescricao =
      'Tente outro nome, confirme a grafia ou peça um código de convite diretamente para a empresa.';

  final String empresaFallback = 'sua empresa';
  final String aguardandoAprovacaoBadge = 'AGUARDANDO APROVAÇÃO';
  final String solicitacaoEnviadaTitulo = 'Solicitação enviada';
  final String solicitacaoEnviadaDescricao =
      'Seu acesso foi encaminhado para análise da empresa. Assim que o vínculo for aprovado, liberamos a próxima etapa automaticamente.';
  final String solicitacaoEmpresaDescricao =
      'Os administradores da empresa receberão sua solicitação e poderão aprovar o vínculo, liberar seus serviços e, se necessário, indicar um motorista operacional.';
  final String prazoEsperadoTitulo = 'Prazo esperado';
  final String prazoEsperadoDescricao =
      'A maioria das análises acontece em até 24 horas úteis. Se a empresa preferir, ela também pode responder usando um código de convite.';
  final String verificando = 'Verificando...';
  final String atualizarStatus = 'Atualizar status';

  final String codigoCorporativoBadge = 'CÓDIGO CORPORATIVO';
  final String inserirCodigoTitulo = 'Inserir código';
  final String inserirCodigoDescricao =
      'Se a empresa já validou seu acesso previamente, basta informar o código corporativo para concluir o vínculo.';
  final String codigoCorporativoHint = 'Ex.: INV-2026-ABCD';
  final String codigoInvalidoOuExpirado = 'Código inválido ou expirado';
  final String validando = 'Validando...';
  final String validarCodigo = 'Validar código';

  final String vinculoConcluidoBadge = 'VÍNCULO CONCLUÍDO';
  final String tudoPronto = 'Tudo pronto!';
  String vinculoConcluidoComEmpresa(String companyName) =>
      'Seu vínculo com $companyName foi concluído com sucesso. Agora você já pode acessar os serviços liberados pela empresa.';
  final String statusDoVinculo = 'Status do vínculo';
  final String statusDoVinculoDescricao =
      'Conta ativa, e-mail verificado e vínculo corporativo salvo.';
  final String irParaOApp = 'Ir para o app';

  final String operacaoEmPreparoBadge = 'OPERAÇÃO EM PREPARO';
  final String empresaVinculadaTitulo = 'Empresa vinculada';
  final String empresaVinculadaDescricao =
      'Seu acesso corporativo já foi liberado. Agora você pode acompanhar serviços liberados enquanto a empresa organiza as viagens do dia.';
  final String proximoPassoOperacionalTitulo = 'Próximo passo operacional';
  final String proximoPassoOperacionalDescricao =
      'Quando houver serviço e viagem operacional para hoje, o app mostra presença, localização, fila de paradas e motorista do dia.';
  final String atualizando = 'Atualizando...';
  final String verificarMotoristaAgora = 'Atualizar operação agora';

  final String acessoCorporativoBadge = 'ACESSO CORPORATIVO';
  final String solicitarAcessoTitulo = 'Solicitar acesso';
  final String solicitarAcessoDescricao =
      'Revise os dados da empresa e confirme o envio. A aprovação será feita diretamente no portal administrativo.';
  final String empresaVerificada = 'VERIFICADA';
  final String processoDeRevisaoTitulo = 'Processo de revisão';
  final String processoDeRevisaoDescricao =
      'Sua solicitação será encaminhada aos administradores da empresa. Eles podem aprovar o vínculo e liberar os serviços recorrentes que você poderá usar.';
  final String enviando = 'Enviando...';
  final String enviarSolicitacao = 'Enviar solicitação';

  final String erroAoCarregarViagem = 'Erro ao carregar viagem';
  final String semViagemHoje = 'Nenhuma viagem prevista agora';
  final String semViagemHojeDescricao =
      'Quando a viagem do passageiro estiver disponível, os detalhes vão aparecer aqui.';
  final String agendaDeHoje = 'Agenda de hoje';
  String viagensNoDia(int total) => '$total viagens hoje';
  String resumoViagensHoje(int total) =>
      total == 1 ? '1 viagem hoje' : '$total viagens hoje';
  final String nenhumServicoLiberadoHoje = 'Nenhum serviço liberado para hoje.';
  final String acompanharViagem = 'Acompanhar viagem';
  final String voltar = 'Voltar';
  final String detalhesDaViagem = 'Detalhes da viagem';
  final String motorista = 'Motorista';
  final String motoristaNaoPublicado = 'Motorista não publicado';
  final String naoPublicado = 'Não publicado';
  String suaParadaNumero(int? ordem) => 'Sua parada • #${ordem ?? '-'}';
  final String voceEoProximoDaFila = 'Você é o próximo da fila.';
  final String voceEoProximo = 'Você é o próximo';
  String paradasAntesDeVoce(int quantidade) =>
      '$quantidade paradas antes de você';
  final String todasAsParadas = 'Todas as paradas';
  final String semParadaPublicada = 'Parada não publicada';
  final String semParadasPublicadas = 'Sem paradas publicadas';
  final String semParadasPublicadasDescricao =
      'A fila da rota ainda não foi publicada para a viagem atual.';
  final String voce = 'Você';
  final String emAndamento = 'Em andamento';
  final String suaParada = 'Sua parada';
  final String planejada = 'Planejada';

  final String sinalAoVivo = 'Sinal ao vivo';
  final String aguardandoSinal = 'Aguardando sinal';
  final String sinalComAtraso = 'Sinal com atraso';
  final String sinalAntigo = 'Sinal antigo';
  final String sinalPausado = 'Sinal pausado';
  final String sinalAoVivoHelper =
      'A posição da van está chegando em tempo real.';
  final String sinalComAtrasoHelper =
      'A última posição chegou com pequeno atraso.';
  final String sinalAntigoHelper =
      'A posição existe, mas pode não refletir o agora.';
  final String sinalPausadoHelper =
      'O motorista pausou ou perdeu o envio de localização.';
  final String aguardandoSinalHelper =
      'Assim que a van publicar posição, o mapa atualiza.';
  final String vanChegando = 'Van chegando';
  final String vanAUmaParada = 'Van a 1 parada';
  String vanAParadas(int quantidade) => 'Van a $quantidade paradas';
  final String viagemEmAcompanhamento = 'Viagem em acompanhamento';
  final String etaAtual = 'ETA atual';
  String paradaResumo(int? ordem, String nome) =>
      'Parada ${ordem ?? '-'} • $nome';
  final String aindaNaoPublicada = 'Ainda não publicada';
  final String posicaoNaFila = 'Posição na fila';
  String posicaoNaFilaResumo(int posicao, int total) => '$posicao de $total';
  final String paradasAntesDeVoceTitulo = 'Paradas antes de você';
  final String navegacao = 'Navegação';
  final String filaDaViagem = 'Fila da viagem';
  final String trechosDaRota = 'Trechos da rota';
  final String filaOperacionalDescricao =
      'A fila operacional da viagem já foi publicada para acompanhamento.';
  String suaParadaNaFrenteDaFila(int totalStops) =>
      'Sua parada está na frente da fila. Total de paradas desta viagem: $totalStops.';
  String filaComParadasAntes(
    int beforeMine,
    int queuePosition,
    int totalStops,
  ) =>
      'Existem $beforeMine parada(s) antes da sua. Sua posição atual na fila é $queuePosition de $totalStops.';
  final String proximaParadaDaViagem = 'Próxima parada da viagem';
  final String paradaOperacionalDaRota = 'Parada operacional da rota';
  final String trechoOperacionalPublicado =
      'Trecho operacional publicado para esta viagem.';
  String distanciaDuracaoTrecho(String distance, String duration) =>
      '$distance • $duration';

  final String aguardandoSaida = 'Aguardando saída';
  final String vanAindaNaoIniciou = 'Van ainda não iniciou';
  final String ultimaPosicaoConhecida = 'Última posição';
  final String aguardandoAtualizacao = 'Aguardando atualização';
  final String suaParadaEProxima = 'Sua parada é a próxima';
  final String prepareSeParaEmbarcar = 'Prepare-se para embarcar';
  final String faltaUmaParada = 'Falta 1 parada';
  String faltamParadas(int stopsBefore) => 'Faltam $stopsBefore paradas';
  final String aguardandoSaidaHelper =
      'A empresa já planejou a viagem e a rota está pronta para sair.';
  final String emAndamentoHelper =
      'O motorista já iniciou a viagem e está se aproximando das paradas.';
  final String finalizada = 'Finalizada';
  final String finalizadaHelper = 'A viagem do dia foi concluída com sucesso.';
  final String cancelada = 'Cancelada';
  final String canceladaHelper = 'A empresa cancelou a viagem de hoje.';
  final String statusIndisponivel = 'Status indisponível';
  final String statusIndisponivelHelper =
      'Ainda não recebemos um status confiável para esta viagem.';
  final String pendente = 'Pendente';
  final String concluida = 'Concluída';
  final String pulada = 'Pulada';
  final String naoEmbarcou = 'Não embarcou';
  final String indisponivel = 'Indisponível';
  final String etaEmCalculo = 'ETA em cálculo';
  final String chegadaIminente = 'Chegada iminente';
  final String umMinuto = '1 min';
  String minutos(int value) => '$value min';
  final String primeiraAtualizacaoEtaHelper =
      'A primeira atualização operacional vai publicar o ETA desta parada.';
  final String paradaProntaHelper =
      'A parada já está pronta para acontecer a qualquer momento.';
  final String previsaoSimplesHelper =
      'Previsão simples publicada para esta etapa da rota.';
  String rotaVersao(int rotaVersao) => 'Rota v$rotaVersao';
  final String rotaDoDia = 'Rota do dia';
  final String rotaDeRuas = 'Rota de ruas';
  final String trajetoAproximado = 'Trajeto aproximado';
  final String rotaNaoPublicadaHelper =
      'A viagem do dia ainda não publicou o trajeto no mapa.';
  final String rotaDeRuasHelper =
      'A rota de ruas calculada já está pronta para a viagem de hoje.';
  final String trajetoAproximadoHelper =
      'O trajeto operacional aproximado está pronto como apoio desta viagem.';
  final String handoffExternoNaoPublicado =
      'Handoff externo ainda não publicado';
  final String mapsProntoRotaDoDia =
      'Navegação pronta para abrir com a rota do dia';
  final String mapsProntoUmaParada =
      'Navegação pronta com 1 parada intermediária';
  String mapsProntoComParadas(int quantidadeWaypoints) =>
      'Navegação pronta com $quantidadeWaypoints paradas intermediárias';

  final String naoFoiPossivelCarregarViagemAtual =
      'Não foi possível carregar a viagem atual do passageiro.';
  final String naoFoiPossivelPromoverSessao =
      'Não foi possível promover a sessão do passageiro.';
  final String naoFoiPossivelConcluirOnboarding =
      'Não foi possível concluir o onboarding da empresa.';
  final String respostaInesperadaRastreio =
      'Resposta inesperada ao carregar o rastreio da viagem.';
  final String naoFoiPossivelCarregarLocalizacaoMotorista =
      'Não foi possível carregar a localização do motorista.';
  final String respostaInesperadaLocalizacaoMotorista =
      'Resposta inesperada ao carregar a localização do motorista.';

  final String seuVeiculo = 'Seu veículo';
  final String localizacaoAoVivo = 'Localização ao vivo';
  final String proximaColeta = 'Próxima coleta';
  String paradaComOrdem(String ordem) => 'Parada $ordem';
  final String carregandoMapaDaViagem = 'Carregando mapa da viagem';
  final String rotaProntaAguardandoSinal =
      'Rota de ruas pronta, aguardando sinal ao vivo';
  final String trajetoProntoAguardandoSinal =
      'Trajeto aproximado pronto, aguardando sinal ao vivo';
  final String rotaComMotoristaAoVivo = 'Rota de ruas com motorista ao vivo';
  final String trajetoComMotoristaAoVivo =
      'Trajeto aproximado com motorista ao vivo';
  final String aguardandoLocalizacaoAoVivo = 'Aguardando localização ao vivo';
  final String mapaIndisponivelNoMomento = 'Mapa indisponível no momento';
  final String buscandoDadosOperacionais =
      'Buscando os dados operacionais e a última posição publicada.';
  final String rotaDeRuasAguardandoPrimeiraPosicao =
      'A rota de ruas já foi publicada. Assim que o motorista enviar a primeira posição, o veículo passa a aparecer sobre esse trajeto.';
  String rotaOperacionalSnapshotAguardandoPrimeiraPosicao(String horario) =>
      'Rota operacional calculada às $horario. Assim que o motorista enviar a primeira posição, o veículo passa a aparecer sobre esse trajeto.';
  final String trajetoAproximadoAguardandoPrimeiraPosicao =
      'O trajeto aproximado de hoje já está pronto. Assim que o motorista enviar a primeira posição, o veículo passa a aparecer sobre essa referência.';
  final String rotaDeRuasComMotoristaDescricao =
      'O motorista já aparece no mapa sobre a rota publicada para esta corrida.';
  String rotaOperacionalSnapshotComMotoristaDescricao(String horario) =>
      'Rota operacional calculada às $horario. O motorista já aparece no mapa sobre esse trajeto.';
  final String trajetoAproximadoComMotoristaDescricao =
      'O motorista já aparece no mapa sobre o trajeto aproximado publicado para esta corrida.';
  final String semCoordenadasPublicadas =
      'Ainda não há coordenadas publicadas para esta viagem.';
  final String aproximarMapa = 'Aproximar mapa';
  final String afastarMapa = 'Afastar mapa';
  final String mapaJaCentralizado = 'Mapa já centralizado';
  final String recentralizarRota = 'Recentralizar rota';

  String conviteAposLogin(String codigo) =>
      'Use seu código de convite após o login: $codigo';
  final String naoFoiPossivelFazerLogin = 'Não foi possível fazer login.';
  final String respostaInvalidaDoServidor = 'Resposta inválida do servidor.';
  final String naoFoiPossivelConcluirLogin =
      'Não foi possível concluir o login.';
  final String entrarParaAcompanharOperar =
      'Entre para acompanhar, operar e organizar a corrida do dia.';
  final String acessarConta = 'Acessar conta';
  final String useMesmoLoginPublicado =
      'Use o mesmo login que a empresa ou a viagem do dia já publicou para o seu perfil.';
  final String email = 'E-mail';
  final String emailInvalido = 'E-mail inválido';
  final String senha = 'Senha';
  final String confirmarSenha = 'Confirmar senha';
  final String minimo6Caracteres = 'Mínimo 6 caracteres';
  final String lembrarDeMimNesteAparelho = 'Lembrar de mim neste aparelho';
  final String entrar = 'Entrar';
  final String criarConta = 'Criar conta';
  final String loginFooterDescricao =
      'Acompanhe a viagem do dia com mapa ao vivo, presença e rastreio sempre em primeiro plano.';

  final String viagemTab = 'Viagem';
  final String presencaTab = 'Presença';
  final String chatsTab = 'Chats';
  final String contaTab = 'Conta';
  final String networkError = 'Erro de conexão';
  final String abrirPresenca = 'Abrir presença';
  final String sinalDeRedeFracoOuInstavel = 'Sinal de rede fraco ou instável.';
  final String viagemNaoSincronizadaNoDispositivo =
      'A viagem ainda não foi sincronizada no dispositivo.';
  final String backendIndisponivelMomentaneamente =
      'Não conseguimos atualizar os dados agora. Tente novamente em instantes.';
  final String todaysSchedule = 'Agenda de hoje';
  final String presenceCheckin = 'Confirmação de presença';
  final String morningCommute = 'Viagem da manhã';
  final String semCorridaMasPodeConfirmar =
      'Mesmo sem uma corrida publicada, você consegue confirmar disponibilidade e manter o embarque organizado.';
  final String confirmAttendance = 'Confirmar presença';
  final String vouEmbarcar = 'Vou embarcar';
  final String avisarEquipeQueVaiUsarVan =
      'Avisar a equipe que você vai usar a van hoje.';
  final String naoVouEmbarcar = 'Não vou embarcar';
  final String manterFilaLimpa =
      'Manter a fila limpa evita atrasos e chamadas desnecessárias.';
  final String rota = 'Rota';
  final String semRota = 'Sem rota';
  final String viagemDoDia = 'Viagem do dia';
  final String aguardandoPublicacao = 'Aguardando publicação';
  final String atualizadoEmTempoReal = 'Atualizado em tempo real';
  final String pickupPoint = 'Ponto de embarque';
  final String paradaAindaNaoPublicada = 'Parada ainda não publicada';
  final String ver = 'Ver';
  final String minhaParada = 'Minha parada';
  final String verDetalhesViagem = 'Ver detalhes da viagem';
  final String mapaCompleto = 'Mapa completo';
  final String confirmarPresencaDescricao =
      'Confirme sua presença para ajudar a empresa e o motorista a organizar a fila do dia.';
  final String futureChat = 'Central de avisos';
  final String futureChatDescricao =
      'Acompanhe avisos da empresa, atualizações da viagem e orientações do motorista em um só lugar.';
  final String conversaOrganizadaUmLugar =
      'Comunicação organizada em um só lugar';
  final String quandoChatEntrarNoProduto =
      'As mensagens importantes da operação ficam reunidas aqui para consulta durante a rotina.';
  final String updates = 'Atualizações';
  final String sharedRides = 'Viagens compartilhadas';
  final String queroSerAvisadoQuandoSair = 'Quero ser avisado quando sair';
  final String passenger = 'Passageiro';
  final String sairDaConta = 'Sair da conta';
  final String viagens = 'Viagens';
  final String dadosOperacionais = 'Dados operacionais';
  final String viagemSelecionada = 'Viagem selecionada';
  final String semViagemSelecionada = 'Sem viagem selecionada';
  final String emailDaConta = 'E-mail da conta';
  final String tripHistory = 'Histórico de viagens';
  final String verCorridasAnteriores = 'Ver corridas anteriores';
  final String paymentMethods = 'Formas de pagamento';
  final String gerenciarCartoesECobranca = 'Gerenciar cartões e cobrança';
  final String oneSaved = '1 salvo';
  final String helpCenter = 'Central de ajuda';
  final String suporteEDuvidasFrequentes = 'Suporte e dúvidas frequentes';
  final String safety = 'Segurança';
  final String recursosDeSegurancaEViagem = 'Recursos de segurança e viagem';
  final String privacy = 'Privacidade';
  final String permissoesEDadosDaConta = 'Permissões e dados da conta';

  final String suaSenhaNaoAtendeRequisitos =
      'Sua senha não atende aos requisitos.';
  final String asSenhasNaoCoincidem = 'As senhas não coincidem.';
  final String preenchaCamposObrigatoriosCadastro =
      'Preencha todos os campos obrigatórios do cadastro.';
  final String erroConexaoServidor = 'Erro de conexão com o servidor.';
  final String crieSuaConta = 'Crie sua conta';
  final String entreNaComunidadeVanRides =
      'Entre na comunidade VanRides e acompanhe sua rota com clareza.';
  final String nome = 'Nome';
  final String sobrenome = 'Sobrenome';
  final String campoObrigatorio = 'Campo obrigatório';
  final String joao = 'João';
  final String silva = 'Silva';
  final String joaoSilvaExemplo = 'joao.silva@exemplo.com';
  final String telefone = 'Telefone';
  final String endereco = 'Endereço';
  final String buscarEnderecoHint = 'Busque sua rua e número';
  final String buscaAutomaticaEnderecoDesabilitada =
      'Busca automática de endereço desabilitada: configure VANRIDES_GOOGLE_PLACES_API_KEY no build.';
  final String enderecoSelecionado = 'Endereço selecionado';
  final String cidade = 'Cidade';
  final String cep = 'CEP';
  final String bairro = 'Bairro';
  final String estado = 'Estado';
  final String ajustarNoMapa = 'Ajustar no mapa';
  final String ajustarNoMapaE2e = 'Ajustar no mapa (E2E desabilitado)';
  final String criarContaPassageiro = 'Criar conta de passageiro';
  final String e2ePreencherEnderecoTeste = 'E2E: preencher endereço de teste';
  final String passageiroCompanyBindingHint =
      'Sua empresa e seu motorista serão definidos depois do primeiro login. Primeiro criamos sua conta e confirmamos seu e-mail; depois você solicita acesso à empresa correta ou valida um código de convite.';
  final String peloMenos8Caracteres = 'Pelo menos 8 caracteres';
  final String umaLetraMaiuscula = 'Uma letra maiúscula (A-Z)';
  final String umaLetraMinuscula = 'Uma letra minúscula (a-z)';
  final String umNumero = 'Um número (0-9)';
  final String umCaractereEspecial = 'Um caractere especial';

  final String conferirSeuEmail = 'Confira seu e-mail';
  final String contaCriadaComSucesso =
      'Sua conta foi criada com sucesso! Para sua segurança, o acesso só será liberado após a confirmação do e-mail.';
  final String motoristaCadastroIniciado =
      'Seu cadastro como motorista parceiro foi iniciado. Confirme seu e-mail para continuar o processo de ativação.';
  final String emailConfirmacaoEnviadoPara =
      'E-MAIL DE CONFIRMAÇÃO ENVIADO PARA';
  final String irParaLogin = 'Ir para login';
  final String editarEmailDepois = 'Editar e-mail depois';
  final String depoisDeConfirmarVolteAoApp =
      'Depois de confirmar, volte ao app para entrar.';
  final String seuEmailAindaNaoFoiConfirmado =
      'Seu e-mail ainda não foi confirmado';
  final String acessoTemporariamenteProtegido =
      'Seu acesso está temporariamente protegido. Por favor, confirme seu e-mail para entrar no VanRides.';
  final String enderecoRegistrado = 'ENDEREÇO REGISTRADO';
  final String jaConfirmeiTentarNovamente = 'Já confirmei, tentar novamente';
  final String reenviando = 'Reenviando...';
  final String reenviarEmail = 'Reenviar e-mail';
  String reenviarEm(String cooldown) => 'Reenviar em $cooldown';
  String novoEnvioEm(String cooldown) =>
      'Você poderá solicitar um novo envio em $cooldown.';

  final String localizacaoDoMotorista = 'Localização do motorista';
  final String mapaCompletoDaViagem = 'Mapa completo da viagem';
  final String atualizarSinal = 'Atualizar sinal';
  final String avatarFallback = 'P';
  final String minhaParadaAindaNaoPublicada =
      'Minha parada ainda não publicada';
  final String etapaDaFilaNaoPublicada =
      'A empresa ainda não publicou a sua etapa da fila.';
  final String paradaConcluida = 'Parada concluída';
  final String etapaConcluidaHoje =
      'A etapa deste passageiro já foi concluída pela corrida de hoje.';
  final String filaEmFocoDescricao =
      'A fila da corrida já colocou a sua parada em foco. Agora o motorista só precisa concluir a etapa atual.';
  final String motoristaChegando = 'Motorista chegando';
  final String aproximacaoForteDaParada =
      'O ETA já indica aproximação forte da sua parada.';
  final String corridaAcompanhadaEmTempoReal =
      'Corrida acompanhada em tempo real';
  final String corridaAcompanhadaDescricao =
      'O mapa mostra a rota completa e deixa claro quantas paradas faltam antes de você.';
  final String semViagemAutomaticaDescricao =
      'Quando a viagem automática do dia for gerada, a rota do dia e a fila de paradas vão aparecer aqui automaticamente.';
  final String motoristaPlural = 'Motorista';
  final String passageirosPlural = 'Passageiros';
  final String vejaTrajetoPublicadoHoje =
      'Veja o trajeto publicado para hoje, as paradas da fila e o motorista sobre a mesma jornada operacional.';
  final String aguardandoDadosDaViagemParaMapa =
      'Aguardando dados da viagem para expandir o mapa.';
  final String viagemProntaParaAcompanhamento =
      'A viagem de hoje esta pronta para acompanhamento.';
  String coordenadasAoVivo(double latitude, double longitude) =>
      'Lat: $latitude | Lng: $longitude';
  String paradaResumoComOrdem(int? ordem, String nome) =>
      'Parada ${ordem ?? '-'} • $nome';
  final String filaDeParadas = 'Fila de paradas';
  final String ajudaPrecisa = 'PRECISA DE AJUDA?';
  final String suporteVanRides = 'Suporte VanRides';
  String erroConexao(Object error) => 'Erro de conexão: $error';
  String erroPadrao(Object error) => 'Erro: $error';
  String presencasResumo(int sucesso, int duplicadas, int erros) =>
      '✅ $sucesso confirmadas | ⚠️ $duplicadas duplicadas | ❌ $erros com erro';
  final String erroAoBuscarPresencas = 'Erro ao buscar presenças.';
  String remocoesResumo(int removidas, int ignoradas, int erros) =>
      '🗑️ $removidas removidas | ⚠️ $ignoradas ignoradas | ❌ $erros com erro';
  final String selecioneAte14Dias =
      'Toque em até 14 dias para confirmar ou remover presença.';
}
