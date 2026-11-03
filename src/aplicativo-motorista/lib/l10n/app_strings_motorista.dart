final class MotoristaStrings {
  const MotoristaStrings();

  final String unableIdentifyCompanyForLiveSignal =
      'Nao foi possivel identificar a empresa para publicar o sinal ao vivo.';
  final String enableLocationToStartLiveSignal =
      'Ative a localizacao para iniciar o sinal ao vivo.';
  final String locationPermissionDeniedLiveMap =
      'Permissao de localizacao negada. O motorista nao aparecera no mapa ao vivo.';
  final String locationPermissionBlockedOpenSettings =
      'Permissao de localizacao bloqueada. Abra os ajustes para ligar o sinal ao vivo.';
  final String unableDetermineLocationPermission =
      'Nao foi possivel determinar a permissao de localizacao.';
  final String liveSignalActiveForegroundOnly =
      'Sinal ao vivo ativo enquanto o app estiver aberto nesta corrida.';

  String routeSavedAs(String routeVersionLabel) =>
      'Rota salva como $routeVersionLabel.';
  final String unableSaveRouteOfDay = 'Nao foi possivel salvar a rota do dia.';
  final String navigationNotReadyForTrip =
      'A navegacao ainda nao esta pronta para esta viagem.';
  final String unableOpenMapsOnDevice =
      'Não foi possível abrir a navegação neste dispositivo.';
  final String tripStartedAndLiveSignalEnabled =
      'Corrida iniciada e sinal ao vivo ativado.';
  final String tripStartedEnableLocationForMap =
      'Corrida iniciada. Ative a localizacao para aparecer no mapa ao vivo.';
  final String unableStartTrip = 'Nao foi possivel iniciar a corrida.';
  final String unableCompleteNextStop =
      'Nao foi possivel concluir a proxima parada.';
  final String unableSkipNextStop = 'Nao foi possivel pular a proxima parada.';
  final String unableNoShowNextStop =
      'Nao foi possivel marcar que o passageiro nao embarcou.';
  final String unableEndTrip = 'Nao foi possivel encerrar a corrida.';
  final String navigationActionQueuedOffline =
      'Acao salva no app para enviar quando a conexao estiver segura.';
  final String navigationActionQueueSynced = 'Acoes locais enviadas.';
  final String navigationActionQueuePartiallySynced =
      'Algumas acoes locais foram enviadas. Ainda ha pendencias.';
  final String navigationActionQueueRetryFailed =
      'Ainda nao foi possivel enviar as acoes locais.';

  final String inicio = 'Inicio';
  final String mapa = 'Mapa';
  final String paradas = 'Paradas';
  final String conta = 'Conta';
  final String appName = 'VanRides';
  final String atualizar = 'Atualizar';
  final String corridaProntaParaDirigir = 'Corrida pronta para dirigir';
  final String corridaProntaParaDirigirDescricao =
      'A viagem do dia aparece com rota publicada, fila de paradas, ETA e navegacao pronta sem precisar montar a jornada do zero.';
  final String erroAoCarregarOperacao = 'Erro ao carregar operacao';
  final String tentarNovamente = 'Tentar novamente';
  final String semViagemPublicadaHoje = 'Sem viagem publicada hoje';
  final String semViagemPublicadaHojeDescricao =
      'Quando a viagem automatica do dia for gerada, a rota operacional aparece aqui com navegacao pronta e fila de paradas.';
  final String agendaDeHoje = 'Agenda de hoje';
  String viagensNoDia(int total) => '$total viagens hoje';
  String resumoViagensHoje(int total) =>
      total == 1 ? '1 viagem hoje' : '$total viagens hoje';
  String resumoParadasHoje(int total) =>
      total == 1 ? '1 parada hoje' : '$total paradas hoje';
  final String viagemSelecionada = 'Viagem selecionada';
  final String mapaCompletoIndisponivel = 'Mapa completo indisponivel';
  final String mapaCompletoIndisponivelDescricao =
      'Quando a viagem do dia estiver pronta, o mapa completo da rota aparece aqui.';
  final String proximaParada = 'Proxima parada';
  final String semProximaParada = 'Sem proxima parada';
  final String eta = 'ETA';
  final String semFilaParaMostrar = 'Sem fila para mostrar';
  final String semFilaParaMostrarDescricao =
      'A lista de paradas aparece aqui assim que a viagem do dia for publicada.';
  final String filaDeParadas = 'Fila de paradas';
  final String semParadasOperacionais = 'Sem paradas operacionais';
  final String semParadasOperacionaisDescricao =
      'Quando a viagem publicar paradas, a fila operacional aparece aqui.';
  final String ordemDasParadas = 'Ordem das paradas';
  final String motoristaOperacional = 'Motorista operacional';
  final String operacaoDeHoje = 'Operacao de hoje';
  final String paradasHoje = 'Paradas hoje';
  final String emailDaConta = 'E-mail da conta';
  final String onTimeRate = 'Pontualidade';
  final String driverRating = 'Avaliação';
  final String shiftHistory = 'Histórico de turnos';
  final String turnosEOperacoesRecentes = 'Turnos e operacoes recentes';
  final String vehicleInfo = 'Dados do veículo';
  final String dadosDoVeiculoERotaAtual = 'Dados do veiculo e rota atual';
  final String support = 'Suporte';
  final String ajudaOperacionalDaEmpresa = 'Ajuda operacional da empresa';
  final String sairDaConta = 'Sair da conta';
  final String avatarFallback = 'M';
  final String emRota = 'Em rota';
  String remainingStops(int remainingStops) => '$remainingStops restantes';
  String progressoParadas(int completed, int total) =>
      '$completed/$total paradas';
  String paradasRestantes(int remainingStops) =>
      remainingStops == 1
          ? '1 parada restante'
          : '$remainingStops paradas restantes';
  final String partidaPronta = 'Partida pronta';
  String primeiraColetaEm(String etaLabel) => 'Primeira coleta em $etaLabel';
  final String iniciandoCorrida = 'Iniciando corrida...';
  final String iniciarCorrida = 'Iniciar Corrida';
  final String iniciarCorridaMin = 'Iniciar corrida';
  final String verLista = 'Ver Lista';
  final String proximaAcao = 'Proxima acao';
  final String revisarRota = 'Revisar rota';
  final String planejamentoProximaAcaoDescricao =
      'Confira a rota e inicie quando estiver pronto para sair.';
  String proximaColetaNome(String nome) => 'Proxima coleta: $nome';
  final String editarRota = 'Editar rota';
  final String corridaConcluida = 'Corrida concluida';
  final String semMaisParadasNestaViagem =
      'Nao existem mais paradas pendentes para esta viagem.';
  final String encerrarCorrida = 'Encerrar corrida';
  final String painelDaParada = 'Painel da parada';
  final String proximoPassageiro = 'Proximo passageiro';
  String stopRouteSummary(int? ordem, String rotaNome) =>
      'Parada ${ordem ?? '-'} • $rotaNome';
  final String semSinalAoVivoNesteAparelho =
      'Sem sinal ao vivo neste aparelho. Ao ativar a localizacao, todos acompanham sua posicao em tempo real.';
  final String sinalAoVivoPausadoAcaoCurta =
      'Ative a localizacao para transmitir.';
  final String atualizandoParada = 'Atualizando parada...';
  final String concluirParada = 'Concluir Parada';
  final String confirmarEmbarque = 'Confirmar embarque';
  final String irParaProximaParada = 'Ir para proxima parada';
  final String abrindoNavegacao = 'Abrindo mapa...';
  String execucaoNavegacaoProximaAcaoDescricao(String nome) =>
      'Siga ate $nome e mantenha o app como painel operacional.';
  String execucaoEmbarqueProximaAcaoDescricao(String nome) =>
      'Quando $nome entrar na van, confirme o embarque.';
  final String maps = 'Navegar';
  final String pular = 'Pular';
  final String naoEmbarcou = 'Nao embarcou';
  final String faltou = 'Faltou';
  final String acoesDeRevisao = 'Acoes de revisao';
  final String acoesDeRevisaoDescricao =
      'Use apenas quando a parada mudar durante a corrida.';
  final String corridaPlanejada = 'Corrida planejada';
  final String corridaEmAndamento = 'Corrida em andamento';
  final String corridaProntaParaSair = 'Corrida pronta para sair';
  final String reviseFilaCompletaAntesDeIniciar =
      'Revise a fila completa nos detalhes antes de iniciar.';
  final String semMaisParadasNestaCorrida =
      'Nao existem mais paradas pendentes nesta corrida.';
  final String planejamentoMapaJornadaInteira =
      'Durante o planejamento o mapa mostra a jornada inteira. Ao iniciar, o foco passa a ser apenas o proximo embarque.';
  final String tripStartedNoLivePositionYet =
      'A corrida ja iniciou, mas este aparelho ainda nao esta publicando sua posicao ao vivo.';
  final String fila = 'Fila';
  final String detalhes = 'Detalhes';
  final String salvandoRota = 'Salvando rota...';
  final String salvarRotaDoDia = 'Salvar rota do dia';
  final String cancelar = 'Cancelar';
  final String viagem = 'Viagem';
  final String visaoGeralDaCorrida = 'Visao geral da corrida';
  final String visaoGeralDaCorridaDescricao =
      'Aqui ficam os detalhes operacionais completos da rota, sem poluir o mapa principal.';
  final String corridaEmAndamentoDescricao =
      'Durante a corrida, esta aba guarda o contexto extra para consulta, enquanto a tela principal fica focada so no proximo embarque.';
  final String acoesDaCorrida = 'Acoes da corrida';
  final String motorista = 'Motorista';
  final String paradasCaption = 'paradas';
  final String confirmadosCaption = 'confirmados';
  final String proximaColeta = 'Proxima coleta';
  final String restantesCaption = 'restantes';
  final String aguardandoDefinicaoOperacional =
      'Aguardando definicao operacional';
  final String etaAtual = 'ETA atual';
  final String filaDeHoje = 'Fila de hoje';
  final String navegacao = 'Navegacao';
  String queueSummary(int queueLength, int confirmados, int pendentes) =>
      '$queueLength parada(s) • $confirmados confirmados • $pendentes pendentes';
  final String todos = 'Todos';
  final String pendentes = 'Pendentes';
  final String pendentesOperacionaisDescricao =
      'Passageiros confirmados que ainda precisam de embarque.';
  final String presencaAConfirmar = 'Presenca a confirmar';
  final String presencaAConfirmarDescricao =
      'Nao competem com a fila ativa ate a presenca estar confirmada.';
  final String confirmados = 'Confirmados';
  String stopRouteCompactSummary(int ordem, String routeName) =>
      'Parada $ordem • $routeName';
  final String proxima = 'Proxima';
  final String proximaParadaDescricao =
      'Foco operacional atual para o motorista.';
  final String presencaConfirmada = 'Presenca confirmada';
  final String presencaPendente = 'Presenca pendente';
  final String resolvidas = 'Resolvidas';
  final String resolvidasDescricao =
      'Concluidas, puladas ou sem embarque ficam como historico.';
  final String menorDeIdade = 'Menor de idade';
  final String modoDeEdicaoDaRota = 'Modo de edicao da rota';
  String routeEditVersionDescription(String routeVersionLabel) =>
      'A ordem salva vira a nova versao operacional da viagem. Atualmente voce esta em $routeVersionLabel.';
  final String primeiraColeta = 'Primeira coleta';
  final String iniciarTripMapDescription =
      'Ao iniciar, o mapa passa a acompanhar o seu deslocamento real.';
  final String veiculoVisivelNoMapa =
      'Seu veiculo ja esta visivel no mapa desta corrida.';
  String nextStopSubtitle(int ordem, String subtitle) =>
      'Parada $ordem • $subtitle';
  final String presencas = 'Presencas';
  final String pularParada = 'Pular parada';
  final String marcarNaoEmbarcou = 'Nao embarcou';
  final String encerrar = 'Encerrar';
  final String confirmarAcao = 'Confirmar';
  final String manterOperacao = 'Cancelar';
  final String confirmarNaoEmbarcouTitulo =
      'Marcar passageiro como nao embarcou?';
  String confirmarNaoEmbarcouMensagem(String nome) =>
      'Confirme apenas se $nome nao entrou na van nesta parada.';
  final String confirmarPularParadaTitulo = 'Pular esta parada?';
  String confirmarPularParadaMensagem(String nome) =>
      'A parada de $nome segue sem embarque registrado e a fila avanca para a proxima.';
  final String confirmarEncerrarCorridaTitulo = 'Encerrar corrida?';
  final String confirmarEncerrarCorridaMensagem =
      'Use quando todas as paradas operacionais ja foram resolvidas.';
  String plannedStopsSummary(int totalStops, String departureLabel) =>
      '$totalStops paradas planejadas • $departureLabel';
  String metricMinutes(int totalMinutes) => '${totalMinutes}min';
  final String visaoGeral = 'Viagem';
  final String filaDetalhada = 'Fila';
  final String sinalAoVivoAindaNaoAtivo =
      'Seu sinal ao vivo ainda nao esta ativo neste aparelho.';

  final String presencasDeHoje = 'Presenças de Hoje';
  final String manifestoDeHoje = 'Lista de hoje';
  final String manifestoOperacional = 'Lista operacional';
  final String erroAoCarregarPresencas = 'Erro ao carregar presenças.';
  String nenhumaPresencaConfirmadaPara(String hojeFormatado) =>
      'Nenhuma presença confirmada para $hojeFormatado.';
  String nenhumaParadaPlanejadaPara(String hojeFormatado) =>
      'Nenhuma parada planejada para $hojeFormatado.';
  String manifestoResumo(int totalParadas, int confirmadas, int pendentes) =>
      '$totalParadas paradas • $confirmadas confirmadas • $pendentes pendentes';
  String parada(int ordem) => 'Parada $ordem';
  String telefone(String telefone) => 'Telefone: $telefone';

  final String compartilharLocalizacao = 'Compartilhar Localização';
  final String porFavorAtiveLocalizacaoNoDispositivo =
      'Por favor, ative a localização no dispositivo';
  final String permissaoDeLocalizacaoNegada = 'Permissão de localização negada';
  final String permissaoLocalizacaoPermanentementeNegada =
      'Permissão de localização permanentemente negada. Habilite em Ajustes.';
  final String abrirAjustes = 'Abrir Ajustes';
  final String localizacaoFuncionaComAppAberto =
      'Localização vai funcionar com o app aberto. Para segundo plano: Ajustes > Localização > Sempre.';
  final String compartilhandoLocalizacaoTempoReal =
      'Compartilhando localização em tempo real';
  final String pararCompartilhamento = 'Parar compartilhamento';
  final String localizacaoNaoEstaSendoCompartilhada =
      'Localização não está sendo compartilhada';
  final String iniciarCompartilhamento = 'Iniciar compartilhamento';
  String erroAoObterLocalizacao(Object error) =>
      'Erro ao obter localização: $error';
  String falhaAoIniciarLocalizacao(Object error) =>
      'Falha ao iniciar localização: $error';

  final String cadastroMotoristaBadge = 'CADASTRO MOTORISTA';
  final String entreParaAFrota = 'Entre para a frota';
  final String perfilProfissionalDescricao =
      'Complete seu perfil profissional para começar a operar viagens com o padrão VanRides.';
  final String nomeCompleto = 'Nome completo';
  final String exemploNomeMotorista = 'Ex.: Jonathan Aris';
  final String criarContaMotorista = 'Criar conta de motorista';
  final String termosMotoristaPrefix =
      'Ao continuar, você concorda com nossos ';
  final String termosParaMotoristas = 'termos para motoristas';
  final String termosMotoristaMiddle = ' e com a ';
  final String politicaDePrivacidade = 'política de privacidade';
  final String jaTemUmaConta = 'Já tem uma conta?';
}
