package org.example.clienteuno;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClienteController implements ControladorDeJogo {

	@FXML private TextField campoMensagem;
	@FXML private TextArea areaMensagens;
	@FXML private Button botaoEnviar;
	@FXML private Button botaoLigar;
	@FXML private TextField campoNome;
	@FXML private TextField campoIP;
	@FXML private TextField campoPorta;
	@FXML private AnchorPane root;
	@FXML private ImageView baralhoView;
	@FXML private ImageView cartaMeio;
	@FXML private HBox maoJogador;
	@FXML private HBox maoOponenteHBox;
	@FXML private Label contadorOponente;
	@FXML private Button botaoComprar;
	@FXML private ChoiceBox<String> escolhaCor;
	@FXML private Button botaoConfirmarCor;

	private DataOutputStream dos;
	private DataInputStream dis;
	private Socket socket;
	private List<Carta> cartasJogador = new ArrayList<>();
	private Carta cartaAtualMeio;
	private int numCartasOponente;
	private LeitorServidor leitorServidor;
	private boolean minhaVez = false;
	private boolean comprouCarta = false;
	private String corEscolhida;
	private boolean jogoIniciado = false; // Novo campo para controlar o estado do jogo

	public void initialize() {
		System.out.println("DEBUG CLIENT CTRL: ClienteController inicializado.");
		if (campoMensagem != null) {
			campoMensagem.setDisable(true);
			botaoEnviar.setDisable(true);
		}
		if (baralhoView != null) {
			try {
				baralhoView.setImage(new Image(getClass().getResourceAsStream("/images/baralho.png")));
				System.out.println("DEBUG CLIENT CTRL: Imagem do baralho carregada.");
			} catch (NullPointerException e) {
				System.err.println("Erro: baralho.png não encontrado em /images/ " + e.getMessage());
				adicionarMensagem("Erro: baralho.png não encontrado.");
			}
		}
		if (escolhaCor != null) {
			escolhaCor.getItems().addAll("Vermelho", "Verde", "Amarelo", "Azul");
			escolhaCor.setVisible(false);
			botaoConfirmarCor.setVisible(false);
		}
	}

	@FXML
	private void ligarServidor() {
		String nome = campoNome.getText().trim();
		if (nome.isEmpty()) {
			adicionarMensagem("Insere um nome.");
			System.out.println("DEBUG CLIENT CTRL: Tentativa de ligar servidor sem nome.");
			return;
		}

		try {
			String ip = campoIP.getText().trim();
			String portaTexto = campoPorta.getText().trim();

			if (ip.isEmpty() || portaTexto.isEmpty()) {
				adicionarMensagem("Insere o IP e a Porta.");
				System.out.println("DEBUG CLIENT CTRL: Tentativa de ligar servidor sem IP/Porta.");
				return;
			}

			int porta;
			try {
				porta = Integer.parseInt(portaTexto);
			} catch (NumberFormatException e) {
				adicionarMensagem("Porta inválida.");
				System.out.println("DEBUG CLIENT CTRL: Porta inválida: " + portaTexto);
				return;
			}

			socket = new Socket(ip, porta);
			adicionarMensagem("Conectado ao servidor. Aguardando início do jogo...");
			System.out.println("DEBUG CLIENT CTRL: Conectado a " + ip + ":" + porta);

			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());

			dos.writeUTF(nome);
			System.out.println("DEBUG CLIENT CTRL: Nome '" + nome + "' enviado ao servidor.");

			leitorServidor = new LeitorServidor(dis, this);
			Thread leitor = new Thread(leitorServidor);
			leitor.setDaemon(true);
			leitor.start();
			System.out.println("DEBUG CLIENT CTRL: Thread LeitorServidor iniciada.");

			campoMensagem.setDisable(false);
			botaoEnviar.setDisable(false);
			botaoLigar.setDisable(true);
			campoNome.setDisable(true);

		} catch (IOException e) {
			adicionarMensagem("Erro ao conectar: " + e.getMessage());
			System.err.println("DEBUG CLIENT CTRL: Erro ao conectar ao servidor: " + e.getMessage());
		}
	}

	@FXML
	private void enviarMensagem() {
		if (dos == null) {
			adicionarMensagem("Erro: Não conectado ao servidor.");
			System.err.println("DEBUG CLIENT CTRL: Tentativa de enviar mensagem sem conexão.");
			return;
		}
		String mensagem = campoMensagem.getText().trim();
		if (!mensagem.isEmpty()) {
			try {
				dos.writeUTF("MENSAGEM:" + mensagem);
				campoMensagem.clear();
				System.out.println("DEBUG CLIENT CTRL: Mensagem enviada: " + mensagem);
			} catch (IOException e) {
				adicionarMensagem("Erro ao enviar: " + e.getMessage());
				System.err.println("DEBUG CLIENT CTRL: Erro ao enviar mensagem: " + e.getMessage());
			}
		}
	}

	@FXML
	public void iniciarJogo() {
		Platform.runLater(() -> {
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/clienteuno/tabuleiro.fxml"));
				AnchorPane root = loader.load();

				TabuleiroController controller = loader.getController();
				controller.inicializarComDados(socket, dis, dos, campoNome.getText().trim(), leitorServidor);

				leitorServidor.setController(controller);
				this.jogoIniciado = true; // Marca que o jogo iniciou
				System.out.println("DEBUG CLIENT CTRL: Jogo iniciado, controller alterado para TabuleiroController.");

				Stage stage = (Stage) botaoLigar.getScene().getWindow();
				stage.setTitle("UNO - Tabuleiro");
				stage.setScene(new Scene(root));
				stage.show();

			} catch (IOException e) {
				e.printStackTrace();
				adicionarMensagem("Erro ao abrir tabuleiro: " + e.getMessage());
				System.err.println("DEBUG CLIENT CTRL: Erro ao abrir tabuleiro: " + e.getMessage());
			}
		});
	}

	public void adicionarMensagem(String mensagem) {
		Platform.runLater(() -> {
			if (areaMensagens != null) {
				areaMensagens.appendText(mensagem + "\n");
			}
		});
	}

	public void atualizarMaoJogador(List<Carta> cartas) {
		Platform.runLater(() -> {
			this.cartasJogador = new ArrayList<>(cartas);
			maoJogador.getChildren().clear();
			System.out.println("DEBUG CLIENT CTRL: Atualizando mão do jogador. Cartas: " + cartas.size());
			for (Carta carta : cartas) {
				try {
					ImageView cartaView = new ImageView(new Image(getClass().getResourceAsStream("/images/" + carta.getNomeImagem() + ".png")));
					cartaView.setFitWidth(80);
					cartaView.setFitHeight(120);
					cartaView.setOnMouseClicked(event -> jogarCarta(carta));
					maoJogador.getChildren().add(cartaView);
				} catch (NullPointerException e) {
					System.err.println("Erro: Imagem " + carta.getNomeImagem() + ".png não encontrada em /images/. " + e.getMessage());
					adicionarMensagem("Erro: Carta " + carta.getNomeImagem() + " não encontrada.");
				}
			}
		});
	}

	public void atualizarCartaMeio(Carta carta) {
		Platform.runLater(() -> {
			this.cartaAtualMeio = carta;
			System.out.println("DEBUG CLIENT CTRL: Atualizando carta do meio para: " + carta.getNomeImagem() + " (Cor: " + carta.getCor() + ")");
			try {
				cartaMeio.setImage(new Image(getClass().getResourceAsStream("/images/" + carta.getNomeImagem() + ".png")));
			} catch (NullPointerException e) {
				System.err.println("Erro: Imagem " + carta.getNomeImagem() + ".png não encontrada em /images/. " + e.getMessage());
				adicionarMensagem("Erro: Carta do meio " + carta.getNomeImagem() + " não encontrada.");
			}
		});
	}

	public void atualizarCartasOponente(int numCartas) {
		Platform.runLater(() -> {
			this.numCartasOponente = numCartas;
			contadorOponente.setText(String.valueOf(numCartas));
			maoOponenteHBox.getChildren().clear();
			System.out.println("DEBUG CLIENT CTRL: Atualizando cartas do oponente: " + numCartas);
			try {
				for (int i = 0; i < numCartas; i++) {
					ImageView cartaView = new ImageView(new Image(getClass().getResourceAsStream("/images/cartavoltada.png")));
					cartaView.setFitWidth(80);
					cartaView.setFitHeight(120);
					maoOponenteHBox.getChildren().add(cartaView);
				}
			} catch (NullPointerException e) {
				System.err.println("Erro: cartavoltada.png não encontrado em /images/. " + e.getMessage());
				adicionarMensagem("Erro: Imagem do oponente não encontrada.");
			}
		});
	}

	private void jogarCarta(Carta carta) {
		System.out.println("DEBUG CLIENT CTRL: Tentativa de jogar carta: " + carta.getNomeImagem() + ". Minha vez: " + minhaVez + ", Comprou carta: " + comprouCarta);
		if (dos == null) {
			adicionarMensagem("Erro: Não conectado ao servidor.");
			return;
		}
		if (leitorServidor.isBloqueioTemporario()) { // Agora sempre false, mas mantido por segurança
			adicionarMensagem("Aguarda a atualização do estado do jogo.");
			return;
		}
		if (!minhaVez) {
			adicionarMensagem("Não é a tua vez! Tenta sincronizar o estado.");
			System.out.println("DEBUG CLIENT CTRL: Jogada negada. Não é a minha vez.");
			return;
		}
		if (escolhaCor.isVisible()) {
			adicionarMensagem("Tens de escolher uma cor antes de jogar.");
			System.out.println("DEBUG CLIENT CTRL: Jogada negada. Escolha de cor pendente.");
			return;
		}
		if (cartaAtualMeio == null) {
			adicionarMensagem("Erro: Carta do meio não definida. Tenta sincronizar.");
			System.err.println("DEBUG CLIENT CTRL: Jogada negada. Carta do meio é null.");
			return;
		}
		if (!cartasJogador.contains(carta)) {
			adicionarMensagem("Erro: Carta não está na tua mão.");
			System.err.println("DEBUG CLIENT CTRL: Jogada negada. Carta " + carta.getNomeImagem() + " não encontrada na mão.");
			return;
		}
		// A validação `podeSerJogadaSobre` já considera a cor escolhida internamente.
		// Apenas garantir que a `Carta` do cliente tenha o método `podeSerJogadaSobre(Carta outra, String corEscolhida)`
		// ou que o servidor faça toda a validação. Idealmente, ambos.
		if (!carta.podeSerJogadaSobre(cartaAtualMeio, corEscolhida)) { // Modified call
			adicionarMensagem("Essa carta não pode ser jogada.");
			System.out.println("DEBUG CLIENT CTRL: Jogada negada. Carta " + carta.getNomeImagem() + " não pode ser jogada sobre " + cartaAtualMeio.getNomeImagem() + " (Cor escolhida: " + (corEscolhida != null ? corEscolhida : "N/A") + ")");
			return;
		}
		try {
			dos.writeUTF("JOGAR:" + carta.getNomeImagem());
			System.out.println("DEBUG CLIENT CTRL: Enviado JOGAR:" + carta.getNomeImagem());
			if (carta.getCor().equals("CORINGA")) {
				escolhaCor.setVisible(true);
				botaoConfirmarCor.setVisible(true);
				System.out.println("DEBUG CLIENT CTRL: Coringa jogado. Ativando escolha de cor.");
			}
			comprouCarta = false;
			dos.writeUTF("REFRESH"); // Added refresh after playing
			System.out.println("DEBUG CLIENT CTRL: Enviado REFRESH após jogar carta.");
		} catch (IOException e) {
			adicionarMensagem("Erro ao jogar carta: " + e.getMessage());
			System.err.println("DEBUG CLIENT CTRL: Erro de IO ao jogar carta: " + e.getMessage());
		}
	}

	@FXML
	private void comprarCarta() {
		System.out.println("DEBUG CLIENT CTRL: Tentativa de comprar carta. Minha vez: " + minhaVez + ", Comprou carta: " + comprouCarta);
		if (dos == null) {
			adicionarMensagem("Erro: Não conectado ao servidor.");
			return;
		}
		if (leitorServidor.isBloqueioTemporario()) { // Agora sempre false
			adicionarMensagem("Aguarda a atualização do estado do jogo.");
			return;
		}
		if (!minhaVez) {
			adicionarMensagem("Não é a tua vez! Tenta sincronizar o estado.");
			System.out.println("DEBUG CLIENT CTRL: Compra negada. Não é a minha vez.");
			return;
		}
		if (escolhaCor.isVisible()) {
			adicionarMensagem("Tens de escolher uma cor antes de comprar.");
			System.out.println("DEBUG CLIENT CTRL: Compra negada. Escolha de cor pendente.");
			return;
		}
		if (comprouCarta) {
			adicionarMensagem("Já compraste uma carta nesta rodada!");
			System.out.println("DEBUG CLIENT CTRL: Compra negada. Já comprei nesta rodada.");
			return;
		}
		try {
			dos.writeUTF("COMPRAR");
			System.out.println("DEBUG CLIENT CTRL: Enviado COMPRAR.");
			comprouCarta = true;
			dos.writeUTF("REFRESH"); // Added refresh after buying
			System.out.println("DEBUG CLIENT CTRL: Enviado REFRESH após comprar carta.");
		} catch (IOException e) {
			adicionarMensagem("Erro ao comprar carta: " + e.getMessage());
			System.err.println("DEBUG CLIENT CTRL: Erro de IO ao comprar carta: " + e.getMessage());
		}
	}

	@FXML
	private void confirmarCor() {
		System.out.println("DEBUG CLIENT CTRL: Tentativa de confirmar cor.");
		if (dos == null) {
			adicionarMensagem("Erro: Não conectado ao servidor.");
			return;
		}
		String corSelecionada = escolhaCor.getValue();
		if (corSelecionada == null) {
			adicionarMensagem("Seleciona uma cor válida!");
			System.out.println("DEBUG CLIENT CTRL: Confirmação de cor negada. Nenhuma cor selecionada.");
			return;
		}
		try {
			String cor = corSelecionada.toUpperCase().charAt(0) + "";
			if (!cor.matches("[VGAB]")) {
				adicionarMensagem("Cor inválida! Escolhe Vermelho, Verde, Amarelo ou Azul.");
				System.out.println("DEBUG CLIENT CTRL: Confirmação de cor negada. Cor inválida: " + cor);
				return;
			}
			dos.writeUTF("COR:" + cor);
			System.out.println("DEBUG CLIENT CTRL: Enviado COR:" + cor);
			corEscolhida = cor;
			escolhaCor.setVisible(false);
			botaoConfirmarCor.setVisible(false);
			System.out.println("DEBUG CLIENT CTRL: Cor " + cor + " confirmada. Desativando escolha de cor.");
			dos.writeUTF("REFRESH"); // Added refresh after confirming color
			System.out.println("DEBUG CLIENT CTRL: Enviado REFRESH após confirmar cor.");
		} catch (IOException e) {
			adicionarMensagem("Erro ao escolher cor: " + e.getMessage());
			System.err.println("DEBUG CLIENT CTRL: Erro de IO ao confirmar cor: " + e.getMessage());
		}
	}

	public void setMinhaVez(boolean minhaVez) {
		this.minhaVez = minhaVez;
		this.comprouCarta = false; // Resetar o flag de compra quando a vez muda
		Platform.runLater(() -> {
			adicionarMensagem(minhaVez ? "É a tua vez!" : "Aguarda a tua vez.");
			System.out.println("DEBUG CLIENT CTRL: setMinhaVez para: " + minhaVez + ". comprouCarta resetado.");
		});
	}

	public void setCorEscolhida(String cor) {
		this.corEscolhida = cor;
		System.out.println("DEBUG CLIENT CTRL: Cor escolhida definida para: " + cor);
	}

	public boolean isJogoIniciado() {
		return jogoIniciado;
	}
}