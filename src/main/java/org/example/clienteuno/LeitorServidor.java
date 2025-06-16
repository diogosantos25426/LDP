package org.example.clienteuno;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

public class LeitorServidor implements Runnable {
	private final DataInputStream dis;
	private ControladorDeJogo controller;
	private final Queue<String> mensagensPendentes = new LinkedList<>();
	// private boolean bloqueioTemporario = false; // Removido para depuração

	public LeitorServidor(DataInputStream dis, ControladorDeJogo controller) {
		this.dis = dis;
		this.controller = controller;
	}

	public void setController(ControladorDeJogo novoController) {
		this.controller = novoController;
		// Processa mensagens pendentes quando o controlador é definido/alterado
		while (!mensagensPendentes.isEmpty()) {
			String msg = mensagensPendentes.poll();
			System.out.println("DEBUG CLIENT LEITOR: Processando mensagem pendente: " + msg);
			processarMensagem(msg);
		}
	}

	@Override
	public void run() {
		try {
			while (true) {
				String mensagem = dis.readUTF();
				System.out.println("DEBUG CLIENT LEITOR: Recebido do servidor: " + mensagem); // Log de mensagem recebida

				if (controller == null || (controller instanceof ClienteController && !((ClienteController) controller).isJogoIniciado())) { // isJogoIniciado() precisa ser adicionado no ClienteController
					// Se o jogo ainda não iniciou ou o controller não é o TabuleiroController
					if (mensagem.equals("INICIAR_JOGO") && controller instanceof ClienteController cliente) {
						System.out.println("DEBUG CLIENT LEITOR: Recebido INICIAR_JOGO. Chamando iniciarJogo().");
						cliente.iniciarJogo();
					} else {
						// Armazenar mensagens até que o TabuleiroController esteja ativo
						mensagensPendentes.add(mensagem);
						System.out.println("DEBUG CLIENT LEITOR: Mensagem adicionada à fila pendente (controller não pronto): " + mensagem);
					}
				} else {
					processarMensagem(mensagem);
				}
			}
		} catch (IOException e) {
			if (controller != null) {
				controller.adicionarMensagem("Conexão perdida com o servidor: " + e.getMessage());
				System.err.println("DEBUG CLIENT LEITOR: Conexão perdida com o servidor: " + e.getMessage());
			}
		}
	}

	public void processarMensagem(String mensagem) {
		// this.bloqueioTemporario = true; // Removido para depuração
		try {
			// Não use Thread.sleep(500) aqui, pois pode causar atrasos e dessincronização
			// Thread.sleep(500);

			if (mensagem.startsWith("CARTAS:")) {
				String cartasStr = mensagem.substring(7);
				List<Carta> cartas = new ArrayList<>();
				if (!cartasStr.isEmpty()) {
					String[] cartaArray = cartasStr.split(";");
					for (String s : cartaArray) {
						String[] partes = s.split(":");
						if (partes.length == 3) {
							cartas.add(new Carta(partes[0], Integer.parseInt(partes[1]), partes[2]));
						}
					}
				}
				controller.atualizarMaoJogador(cartas);
				System.out.println("DEBUG CLIENT LEITOR: Mão do jogador atualizada. Cartas: " + cartas.size());

			} else if (mensagem.startsWith("MEIO:")) {
				String[] partes = mensagem.substring(5).split(":");
				if (partes.length >= 1) { // Pode ter nome, valor, cor
					String nomeImagem = partes[0];
					int valor = (partes.length > 1) ? Integer.parseInt(partes[1]) : 0; // Default 0
					String cor = (partes.length > 2) ? partes[2] : "N/A"; // Default N/A

					Carta cartaMeio = new Carta(nomeImagem, valor, cor);
					controller.atualizarCartaMeio(cartaMeio);
					System.out.println("DEBUG CLIENT LEITOR: Carta do meio atualizada: " + cartaMeio.getNomeImagem() + " (Cor: " + cor + ")");

					// Se for uma carta coringa e o cliente precisa escolher a cor,
					// o servidor envia "MENSAGEM:Escolha uma cor." separadamente.
					// A cor escolhida já vem no MEIO ou na mensagem COR, mas é para o próximo turno.
					// Aqui apenas definimos a cor atual do jogo (se foi um coringa jogado anteriormente e a cor foi definida)
					if (controller instanceof ClienteController) {
						((ClienteController) controller).setCorEscolhida(cor);
					} else if (controller instanceof TabuleiroController) {
						((TabuleiroController) controller).setCorEscolhida(cor);
					}
				} else {
					controller.adicionarMensagem("Erro ao interpretar carta do meio: " + mensagem);
					System.err.println("DEBUG CLIENT LEITOR: Erro ao interpretar carta do meio: " + mensagem);
				}

			} else if (mensagem.startsWith("OPONENTE:")) {
				int numCartas = Integer.parseInt(mensagem.substring(9).trim());
				controller.atualizarCartasOponente(numCartas);
				System.out.println("DEBUG CLIENT LEITOR: Cartas do oponente atualizadas: " + numCartas);

			} else if (mensagem.startsWith("MENSAGEM:")) {
				controller.adicionarMensagem(mensagem.substring(9).trim());
				System.out.println("DEBUG CLIENT LEITOR: Mensagem do servidor: " + mensagem.substring(9).trim());

			} else if (mensagem.equals("SUA_VEZ")) {
				if (controller instanceof ClienteController) {
					((ClienteController) controller).setMinhaVez(true);
				} else if (controller instanceof TabuleiroController) {
					((TabuleiroController) controller).setMinhaVez(true);
				}
				controller.adicionarMensagem("É a tua vez!");
				System.out.println("DEBUG CLIENT LEITOR: Recebido SUA_VEZ.");

			} else if (mensagem.equals("AGUARDE")) {
				if (controller instanceof ClienteController) {
					((ClienteController) controller).setMinhaVez(false);
				} else if (controller instanceof TabuleiroController) {
					((TabuleiroController) controller).setMinhaVez(false);
				}
				controller.adicionarMensagem("Aguarda a tua vez.");
				System.out.println("DEBUG CLIENT LEITOR: Recebido AGUARDE.");

			} else {
				controller.adicionarMensagem("Mensagem desconhecida: " + mensagem);
				System.out.println("DEBUG CLIENT LEITOR: Mensagem desconhecida: " + mensagem);
			}

		} catch (Exception e) {
			System.err.println("DEBUG CLIENT LEITOR: Erro ao processar mensagem '" + mensagem + "': " + e.getMessage());
			e.printStackTrace();
			if (controller != null) {
				controller.adicionarMensagem("Erro ao processar mensagem: " + e.getMessage());
			}
		}
		// this.bloqueioTemporario = false; // Removido para depuração
	}

	public boolean isBloqueioTemporario() {
		return false; // Sempre retorna false agora que foi removido
	}
}