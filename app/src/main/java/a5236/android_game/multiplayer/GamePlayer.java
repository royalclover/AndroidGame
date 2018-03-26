package a5236.android_game.multiplayer;

import android.util.Log;

import a5236.android_game.MC_Question;
import a5236.android_game.Player;
import a5236.android_game.ScoreboardFragment;
import a5236.android_game.multiplayer.packet.Packet;
import a5236.android_game.multiplayer.packet.PacketBuilder;
import a5236.android_game.multiplayer.packet.PacketReader;

import com.google.android.gms.games.RealTimeMultiplayerClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class GamePlayer {

    private static final String TAG = "GamePlayer";

    public MultiplayerClient multiplayerClient;

    public static final RealTimeMultiplayerClient.ReliableMessageSentCallback callback = new RealTimeMultiplayerClient.ReliableMessageSentCallback() {
        @Override
        public void onRealTimeMessageSent(int i, int i1, String s) {
        }
    };

    private Map<Short, PacketHandler> packetHandlers = new HashMap<>();

    private Queue<Packet> receivedPackets = new LinkedList<>();

    public Player player;
    public List<Player> players;

    GamePlayer(final MultiplayerClient multiplayerClient, final Player player, final List<Player> players) {
        this.multiplayerClient = multiplayerClient;

        this.player = player;
        this.players = players;

        // Send message
        setPacketHandler(1, new PacketHandler() {
            @Override
            public void handlePacket(PacketReader reader) {
                try {
                    String message = reader.readString();
                    Log.d(TAG, "Received message: " + message);
                } catch (IOException ignored) {
                }
            }
        });
        // Show mini-game wheel fragment
        setPacketHandler(10, new PacketHandler() {
            @Override
            public void handlePacket(PacketReader reader) {
                // Logic to display current round for a few seconds, then show wheel choosing mini-game for the round
                try {
                    int round = (int) reader.readByte();
                    String minigameName = reader.readString();
                    Log.d(TAG, "Showing mini-game wheel for round: " + round + " and chosen minigame: " + minigameName);
                    // TODO: Show fragment with current round and mini-game wheel

                    // TODO: Move this elsewhere and call when mini-game wheel fragment is finished
                    sendToHost(buildMinigameReadyPacket(player));
                } catch (IOException ignored) {
                }
            }
        });
        // Show multiple choice mini-game
        setPacketHandler(20, new PacketHandler() {
            @Override
            public void handlePacket(PacketReader reader) {
                // Logic to get question and answers to display, and show fragment for player to select answer
                try {
                    String question = reader.readString();
                    String a = reader.readString();
                    String b = reader.readString();
                    String c = reader.readString();
                    String d = reader.readString();
                    String answer = reader.readString();
                    MC_Question mcQuestion = new MC_Question(question, a, b, c, d, answer);
                    Log.d(TAG, "Showing multiple choice mini-game");
                    // TODO: Show fragment with the given question

                    // TODO: Reply to host with packet to select answer
                    sendToHost(buildMultipleChoiceSubmitAnswerPacket(player, answer));
                } catch (IOException ignored) {
                }
            }
        });
        // Show mini-game results / scoreboard fragment
        setPacketHandler(30, new PacketHandler() {
            @Override
            public void handlePacket(PacketReader reader) {
                Log.d(TAG, "Showing current scoreboard fragment for game");
                Player[] playerArray = null;
                try {
                    int playerAmount = (int) reader.readByte();
                    playerArray = new Player[playerAmount];
                    for (int i = 0; i < playerAmount; i++) {
                        String playerId = reader.readString();
                        String playerName = reader.readString();
                        int playerPoints = reader.readInt();
                        int roundsWon = (int) reader.readByte();
                        Player p = new Player(playerId, playerName);
                        p.setPoints(playerPoints);
                        p.setRounds_won(roundsWon);
                        playerArray[i] = p;
                    }
                } catch (IOException ignored) {
                }

                // TODO: Show scoreboard for mini-game or final results
                ScoreboardFragment scoreboardFragment = ScoreboardFragment.newInstance(playerArray);

                // TODO: Move this elsewhere and call when scoreboard fragment is finished
                sendToHost(buildScoreboardContinuePacket(player));
            }
        });
        // End finished minigame, return to main menu
        setPacketHandler(40, new PacketHandler() {
            @Override
            public void handlePacket(PacketReader reader) {
                Log.d(TAG, "Minigame finished, returning to main menu");
                multiplayerClient.leaveGame();
                // TODO: Return to main menu..
            }
        });
    }

    public interface PacketHandler {
        void handlePacket(PacketReader reader);
    }

    public void setPacketHandler(int id, PacketHandler handler) {
        packetHandlers.put((short) id, handler);
    }

    public void handlePacket(Packet packet) {
        receivedPackets.add(packet);
    }

    public Packet buildMessagePacket(String message) {
        return new PacketBuilder(Packet.PacketType.Request)
                .withID((short) 1)
                .withString(message)
                .build();
    }

    public Packet buildMinigameWheelPacket(int round, String minigameName) {
        return new PacketBuilder(Packet.PacketType.Request)
                .withID((short) 10)
                .withByte((byte) round)
                .withString(minigameName)
                .build();
    }

    public Packet buildMinigameReadyPacket(Player player) {
        return new PacketBuilder(Packet.PacketType.Reply)
                .withID((short) 11)
                .withString(player.getParticipantId())
                .build();
    }

    public Packet buildMultipleChoiceMinigamePacket(MC_Question question) {
        return new PacketBuilder(Packet.PacketType.Request)
                .withID((short) 20)
                .withString(question.getQuestion())
                .withString(question.getChoiceA())
                .withString(question.getChoiceB())
                .withString(question.getChoiceC())
                .withString(question.getChoiceD())
                .withString(question.getAnswer())
                .build();
    }

    public Packet buildMultipleChoiceSubmitAnswerPacket(Player player, String answer) {
        return new PacketBuilder(Packet.PacketType.Reply)
                .withID((short) 21)
                .withString(player.getParticipantId())
                .withString(answer)
                .build();
    }

    public Packet buildScoreboardPacket(List<Player> players) {
        PacketBuilder builder = new PacketBuilder(Packet.PacketType.Request);
        builder.withID((short) 30);
        builder.withByte((byte) players.size());
        for (Player player : players) {
            builder.withString(player.getParticipantId());
            builder.withString(player.getDisplayName());
            builder.withInt(player.getPoints());
            builder.withByte((byte) player.getRounds_won());
        }
        return builder.build();
    }

    public Packet buildScoreboardContinuePacket(Player player) {
        return new PacketBuilder(Packet.PacketType.Reply)
                .withID((short) 31)
                .withString(player.getParticipantId())
                .build();
    }

    public Packet buildFinishGamePacket() {
        return new PacketBuilder(Packet.PacketType.Request)
                .withID((short) 40)
                .build();
    }

    public void sendToHost(Packet packet) {
        multiplayerClient.sendToHost(packet, callback);
    }

    public void gameTick() {
        while (!receivedPackets.isEmpty()) {
            Packet packet = receivedPackets.remove();
            PacketReader reader = new PacketReader(packet);
            PacketHandler handler = packetHandlers.get(packet.getPacketID());
            if (handler != null) {
                handler.handlePacket(reader);
            }
        }
    }

}
