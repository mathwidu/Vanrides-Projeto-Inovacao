final class GuardianStrings {
  const GuardianStrings();

  final String naoFoiPossivelCarregarPassageirosVinculados =
      'Não foi possível carregar os passageiros vinculados.';
  final String dashboardBadge = 'Painel da família';
  String bemVindo(String guardianName) => 'Bem-vindo, $guardianName';
  final String dashboardDescricao =
      'As jornadas da sua família estão ativas e sendo acompanhadas.';
  final String passageirosVinculados = 'Passageiros vinculados';
  final String menoresDeIdade = 'Menores de idade';
  final String passageirosDaFamilia = 'Passageiros da família';
  final String acompanhamentoHoje = 'Acompanhamento de hoje';
  final String proximaAcao = 'Próxima ação';
  final String presencaConfirmadaHoje = 'Presença confirmada hoje';
  final String presencaPendenteHoje = 'Presença pendente hoje';
  final String presencaNaoPublicadaHoje = 'Presença de hoje na aba Presenças';
  final String acompanharLocalizacao = 'Acompanhar localização';
  final String revisarPresenca = 'Revisar presença';
  final String abrirDetalhes = 'Abrir detalhes';
  final String aguardandoSinalDaVan = 'Aguardando sinal da van';
  final String viagemDefineMotorista = 'Viagem do dia define motorista';
  final String viagemAindaNaoPublicada = 'Viagem do dia ainda não publicada';
  String ultimaPosicaoAs(String horario) => 'Última posição às $horario';
  final String pessoaAcompanhada = 'Pessoa acompanhada';
  final String acompanhamentoSeguro = 'Acompanhamento seguro';
  final String vanDaViagem = 'Van da viagem';
  final String minhaParada = 'Minha parada';
  final String ultimaAtualizacao = 'Última atualização';
  final String statusDaViagem = 'Status da viagem';
  String atualizadaAs(String horario) => 'Atualizada às $horario';
  final String atualizar = 'Atualizar';
  final String erroDeRede = 'Erro de rede';
  final String listaNaoSincronizada =
      'A lista vinculada pode ainda não ter sido sincronizada.';
  final String conexaoFraca = 'Sua conexão pode estar fraca ou instável.';
  final String apiProcessandoAtualizacao =
      'A API pode estar processando a atualização do vínculo.';
  final String nenhumPassageiroVinculado = 'Nenhum passageiro vinculado';
  final String nenhumPassageiroVinculadoDescricao =
      'Quando a empresa vincular passageiros a esta família, eles vão aparecer aqui com contexto de rota e acompanhamento.';
  String motoristaAssociado(String nome) => 'Motorista cadastrado: $nome';
  final String aCaminho = 'A CAMINHO';
  final String familia = 'FAMÍLIA';
  final String enderecoNaoInformado = 'Endereço ainda não informado';
  final String detalhes = 'Detalhes';

  final String naoFoiPossivelCarregarPresencas =
      'Não foi possível carregar as presenças do passageiro vinculado.';
  final String limiteDiasSelecionados = 'Limite de 14 dias selecionados.';
  String resumoConfirmacao(int confirmadas, int duplicadas, int erros) =>
      'Confirmadas: $confirmadas | Duplicadas: $duplicadas | Erros: $erros';
  String resumoRemocao(int removidas, int ignoradas, int erros) =>
      'Removidas: $removidas | Ignoradas: $ignoradas | Erros: $erros';
  final String presencasDoResponsavel = 'Presenças do responsável';
  final String presencasDescricao =
      'Status em tempo real de todos os alunos vinculados para hoje.';
  final String aindaNaoHaPassageiros =
      'Ainda não há passageiros vinculados para este responsável.';
  final String passageirosComAcesso = 'Passageiros com acesso';
  final String comAcesso = 'com acesso';
  final String presencasConfirmadas = 'Presenças confirmadas';
  final String confirmadas = 'confirmadas';
  final String diasSelecionados = 'Dias selecionados';
  final String servicosDeHoje = 'Serviços de hoje';
  final String nenhumServicoLiberadoHoje = 'Nenhum serviço liberado hoje';
  final String nenhumServicoLiberadoHojeDescricao =
      'Quando a empresa liberar uma linha para este passageiro, a decisão aparece aqui.';
  final String nenhumHorarioParaDecidirHoje =
      'Nenhum horário para decidir hoje';
  String nenhumHorarioParaDecidirHojeDescricao(String nome) =>
      '$nome não tem horários liberados para hoje.';
  String presencaPrecisaDecidirHoje(String nome, int quantidade) =>
      '$nome precisa decidir $quantidade ${quantidade == 1 ? 'horário' : 'horários'} hoje';
  String presencaTudoDecididoHoje(String nome) =>
      'Tudo decidido para $nome hoje';
  final String reviseHorariosLiberados =
      'Revise os horários liberados e escolha Vou ou Não vou.';
  final String decisoesDeHojeRegistradas =
      'As decisões de hoje já foram registradas. Você ainda pode alterar abaixo.';
  String horariosLiberadosHoje(int quantidade) =>
      '$quantidade ${quantidade == 1 ? 'horário liberado' : 'horários liberados'}';
  String horariosPendentesHoje(int quantidade) =>
      '$quantidade ${quantidade == 1 ? 'pendente' : 'pendentes'}';
  final String selecionarPassageiro = 'Selecionar passageiro';
  String motoristaAssociadoDetalhe(String nome) =>
      'Motorista cadastrado: $nome';
  final String semMotoristaAssociado = 'Motorista definido na viagem do dia.';
  final String passageiroMenorDeIdade = 'Passageiro menor de idade';
  final String passageiroAdulto = 'Passageiro adulto';
  final String confirmarSelecionados = 'Confirmar selecionados';
  final String removerSelecionados = 'Remover selecionados';

  final String inicio = 'Início';
  final String presencas = 'Presenças';
  final String localizacao = 'Localização';
  final String conta = 'Conta';
  final String responsavelFamiliar = 'Responsável familiar';
  final String contaDoResponsavel = 'Conta do responsável';
  final String sairDaConta = 'Sair da conta';
  final String perfilBackend = 'Perfil recebido';
  final String familiaAtiva = 'Família';
  final String ativa = 'Ativa';
  final String empresa = 'Empresa';
  final String ativo = 'Ativo';
  final String email = 'E-mail';
  final String segurancaESuporte = 'Segurança e suporte';
  final String centralDeConfianca = 'Central de confiança da família';

  final String naoFoiPossivelCarregarRastreio =
      'Não foi possível carregar o rastreio da viagem.';
  final String passageiroVinculado = 'Passageiro vinculado';
  final String passageiroSelecionado = 'Passageiro selecionado';
  final String estePassageiroEoProximoDaFila =
      'Este passageiro é o próximo da fila';
  String paradasAntesDele(int stopsBefore) =>
      '$stopsBefore parada(s) antes dele';
  final String paradasAntesDoAluno = 'Paradas antes do aluno';
  String passageiroEoProximo(String nome) => '$nome é o próximo';
  String paradasRestantes(int stopsBefore) =>
      '$stopsBefore parada(s) restantes';
  final String motorista = 'Motorista';
  final String naoPublicado = 'Não publicado';
  final String mapaCompleto = 'Mapa completo';
  final String trocarPassageiro = 'Trocar passageiro';
  final String agendaDeHoje = 'Agenda de hoje';
  String viagensNoDia(int total) => '$total viagens hoje';
  String passageiroJaEmbarcou(String nome) => '$nome já embarcou';
  final String passageiroJaEmbarcouDescricao =
      'A parada deste passageiro já foi concluída nesta corrida.';
  String passageiroProximo(String nome) => '$nome é o próximo';
  final String passageiroProximoDescricao =
      'A fila da corrida já colocou este passageiro como próxima parada.';
  String faltamParadas(int quantidade) => 'Faltam $quantidade parada(s)';
  final String faltamParadasDescricao =
      'O mapa deixa claro quantas paradas ainda existem antes deste passageiro.';
  final String viagemEmAcompanhamento = 'Viagem em acompanhamento';
  final String viagemEmAcompanhamentoDescricao =
      'O responsável enxerga a mesma jornada do motorista, mas com foco na parada do passageiro vinculado.';
  final String rastreioDaViagem = 'Rastreio da viagem';
  final String rastreioDescricao =
      'Acompanhe a rota real do passageiro vinculado, a fila da viagem e a posição atual do motorista no mesmo contexto.';
  final String erroAoCarregarPassageiros = 'Erro ao carregar passageiros';
  final String tentarNovamente = 'Tentar novamente';
  final String nenhumPassageiroVinculadoRastreioDescricao =
      'Quando a empresa vincular um passageiro ao responsável, ele vai aparecer aqui para acompanhamento.';
  final String erroAoCarregarRastreio = 'Erro ao carregar rastreio';
  final String semViagemHoje = 'Sem viagem hoje';
  final String semViagemHojeDescricao =
      'Quando a viagem automática do dia for gerada, a rota e a fila de paradas deste passageiro vão aparecer aqui.';
  final String responsavel = 'Responsável';
  String acompanheViagemDe(String nome) => 'Acompanhe a viagem de $nome';
  final String subheadlineRastreioPassageiro =
      'O mesmo mapa principal do passageiro organiza rota do dia, motorista e próxima parada em uma leitura direta.';
  final String passageiroEoProximoDaRota =
      'Este passageiro é o próximo da rota';
  final String rotaEmAcompanhamento = 'Rota em acompanhamento';
  final String semParadaPublicada =
      'Sem parada publicada para este passageiro.';
  String paradaResumoComStatus(int ordem, String status, String rotaVersao) =>
      'Parada $ordem • $status • $rotaVersao';
  final String expandirMapa = 'Expandir mapa';
  String endereco(String enderecoResumo) => 'Endereço: $enderecoResumo';
  final String atualizacoesRecentes = 'Atualizações recentes';
  final String nenhumPontoLocalizacao =
      'Nenhum ponto de localização foi recebido ainda.';
  final String menorDeIdade = 'Menor de idade';
  final String status = 'Status';
  final String aluno = 'Aluno';
  final String vinculo = 'Vínculo';
  final String semMotorista = 'Sem motorista';
  final String vinculoAtivo = 'Vínculo ativo';
  final String dadosBasicos = 'Dados básicos';
  final String telefone = 'Telefone';
  final String cep = 'CEP';
  final String enderecoLabel = 'Endereço';
  final String contextoDaFamilia = 'Contexto da família';
  final String contextoDaFamiliaDescricao =
      'Esta ficha deixa o responsável entender quem está vinculado, quais fluxos usar no app e que o motorista operacional vem da viagem do dia.';
  final String abaPresencasDescricao =
      'As presenças do responsável ficam na aba Presenças do painel.';
  final String abrirAbaPresencas = 'Abrir a aba Presenças';
  final String abaLocalizacaoDescricao =
      'Localização e status ficam na aba Localização do painel.';
  final String abrirAbaLocalizacao = 'Abrir a aba Localização';
  final String naoInformado = 'Não informado';

  final String naoFoiPossivelCarregarLocalizacaoMotorista =
      'Não foi possível carregar a localização do motorista da viagem.';
  final String respostaInesperadaHistorico =
      'Resposta inesperada ao carregar o histórico de localização.';
  final String naoFoiPossivelCarregarPassageirosDoResponsavel =
      'Não foi possível carregar os passageiros vinculados do responsável.';
  final String naoFoiPossivelConfirmarPresenca =
      'Não foi possível confirmar a presença do passageiro vinculado.';
  final String naoFoiPossivelRemoverPresenca =
      'Não foi possível remover a presença do passageiro vinculado.';
}
