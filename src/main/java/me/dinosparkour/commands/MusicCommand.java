/*
 * (C) Copyright 2016 Dinos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.dinosparkour.commands;

import com.mashape.unirest.http.Unirest;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import me.dinosparkour.Info;
import me.dinosparkour.audio.AudioInfo;
import me.dinosparkour.audio.AudioPlayerSendHandler;
import me.dinosparkour.audio.TrackManager;
import me.dinosparkour.utils.MessageUtil;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MusicCommand extends Command {

    private static final AudioPlayerManager myManager = new DefaultAudioPlayerManager();
    private static final Map<String, Map.Entry<AudioPlayer, TrackManager>> players = new HashMap<>();

    private static final String CD = "\uD83D\uDCBF";
    private static final String DVD = "\uD83D\uDCC0";
    private static final String MIC = "\uD83C\uDFA4 **|>** ";

    private static final String QUEUE_TITLE = "__%s has added %d new track%s to the Queue:__";
    private static final String QUEUE_DESCRIPTION = "%s **|>**  %s\n%s\n%s %s\n%s";
    private static final String QUEUE_INFO = "Info about the Queue: (Size - %d)";

    public MusicCommand() {
        AudioSourceManagers.registerRemoteSources(myManager);
    }

    @Override
    public void executeCommand(String[] args, MessageReceivedEvent e, MessageSender chat) {
        Guild guild = e.getGuild();
        switch (args.length) {
            case 0: // Show help message
                sendHelpMessage(chat);
                break;

            case 1:
                switch (args[0].toLowerCase()) {
                    case "help":
                    case "me/dinosparkour/commands":
                        sendHelpMessage(chat);
                        break;

                    case "now":
                    case "current":
                    case "nowplaying":
                    case "info": // Display song info
                        if (!hasPlayer(guild) || getPlayer(guild).getPlayingTrack() == null) { // No song is playing
                            chat.sendMessage("No song is being played at the moment! *It's your time to shine..*");
                        } else {
                            AudioTrack track = getPlayer(guild).getPlayingTrack();
                            chat.sendEmbed("Track Info", String.format(QUEUE_DESCRIPTION, CD, getOrNull(track.getInfo().title),
                                    "\n\u23F1 **|>**  " + getTimestamp(track.getPosition() / 1000),
                                    "\n" + MIC, getOrNull(track.getInfo().author),
                                    "\n\uD83C\uDFA7 **|>**  " + MessageUtil.userDiscrimSet(getTrackManager(guild).getTrackInfo(track).getAuthor().getUser())));
                        }
                        break;

                    case "queue":
                        if (!hasPlayer(guild) || getTrackManager(guild).getQueuedTracks().isEmpty()) {
                            chat.sendMessage("The queue is empty! Load a song with **"
                                    + MessageUtil.stripFormatting(Info.PREFIX) + "music play**!");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            Set<AudioInfo> queue = getTrackManager(guild).getQueuedTracks();
                            queue.forEach(audioInfo -> sb.append(buildQueueMessage(audioInfo)));
                            String embedTitle = String.format(QUEUE_INFO, queue.size());

                            if (sb.length() <= 1960) {
                                chat.sendEmbed(embedTitle, "**>** " + sb.toString());
                            } else if (sb.length() <= 20000) {
                                chat.sendEmbed(embedTitle, "[Click here for a detailed list](https://hastebin.com/"
                                        + new JSONObject(Unirest.post("https://hastebin.com/documents").body(sb.toString()).getBody().toString()).getString("key")
                                        + ".txt)");
                            } else {
                                e.getChannel().sendTyping().queue();
                                File qFile = new File("queue.txt");
                                try {
                                    FileUtils.write(qFile, sb.toString(), "UTF-8", false);
                                    e.getChannel().sendFile(qFile, qFile.getName(), null).queue();
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }

                                if (!qFile.delete()) { // Delete the queue file after we're done
                                    qFile.deleteOnExit();
                                }
                            }
                        }
                        break;

                    case "skip":
                        if (isIdle(chat, guild)) return;

                        if (isCurrentDj(e.getMember())) {
                            forceSkipTrack(guild, chat);
                        } else {
                            AudioInfo info = getTrackManager(guild).getTrackInfo(getPlayer(guild).getPlayingTrack());
                            if (info.hasVoted(e.getAuthor())) {
                                chat.sendMessage("\u26A0 You've already voted to skip this song!");
                            } else {
                                int votes = info.getSkips();
                                if (votes <= 3) { // Skip on 4th vote
                                    getPlayer(guild).stopTrack();
                                    chat.sendMessage("\u23E9 Skipping current track.");
                                } else {
                                    info.addSkip(e.getAuthor());
                                    chat.sendMessage("**" + MessageUtil.userDiscrimSet(e.getAuthor()) + "** has voted to skip this track! [" + (votes + 1) + "/4]");
                                    tryToDelete(e);
                                }
                            }
                        }
                        break;

                    case "forceskip":
                        if (isIdle(chat, guild)) return;

                        if (isCurrentDj(e.getMember()) || isDj(e.getMember())) {
                            forceSkipTrack(guild, chat);
                        } else {
                            chat.sendMessage("You don't have permission to do that!\n"
                                    + "Use **" + MessageUtil.stripFormatting(Info.PREFIX) + "music skip** to cast a vote!");
                        }
                        break;

                    case "reset":
                        if (!isDj(e.getMember())) {
                            chat.sendMessage("You don't have the required permissions to do that! [DJ role]");
                        } else {
                            players.remove(guild.getId());
                            getPlayer(guild).destroy();
                            getTrackManager(guild).purgeQueue();
                            guild.getAudioManager().closeAudioConnection();
                            chat.sendMessage("\uD83D\uDD04 Resetting the music player..");
                        }
                        break;

                    case "shuffle":
                        if (isIdle(chat, guild)) return;

                        if (isDj(e.getMember())) {
                            getTrackManager(guild).shuffleQueue();
                            chat.sendMessage("\u2705 Shuffled the queue!");
                        } else {
                            chat.sendMessage("\u26D4 You don't have the permission to do that!");
                        }
                        break;
                }

            default:
                String input = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                switch (args[0].toLowerCase()) {
                    case "play": // Play a track
                        loadTrack(input, e.getMember(), chat);
                        tryToDelete(e);
                        break;
                }
                break;
        }
    }

    @Override
    public List<String> getAlias() {
        return Collections.singletonList("music");
    }

    private void tryToDelete(MessageReceivedEvent e) {
        if (e.getGuild().getSelfMember().hasPermission(e.getTextChannel(), Permission.MESSAGE_MANAGE)) {
            e.getMessage().deleteMessage().queue();
        }
    }

    private boolean hasPlayer(Guild guild) {
        return players.containsKey(guild.getId());
    }

    private AudioPlayer getPlayer(Guild guild) {
        AudioPlayer p;
        if (hasPlayer(guild)) {
            p = players.get(guild.getId()).getKey();
        } else {
            p = createPlayer(guild);
        }
        return p;
    }

    private TrackManager getTrackManager(Guild guild) {
        return players.get(guild.getId()).getValue();
    }

    private AudioPlayer createPlayer(Guild guild) {
        AudioPlayer nPlayer = myManager.createPlayer();
        TrackManager manager = new TrackManager(nPlayer);
        nPlayer.addListener(manager);
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(nPlayer));
        players.put(guild.getId(), new AbstractMap.SimpleEntry<>(nPlayer, manager));
        return nPlayer;
    }

    private void loadTrack(String identifier, Member author, Command.MessageSender chat) {
        Guild guild = author.getGuild();
        getPlayer(guild); // Make sure this guild has a player.

        myManager.loadItemOrdered(guild, identifier, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack track) {
                loadSingle(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getSelectedTrack() != null) {
                    loadSingle(playlist.getSelectedTrack());
                } else {
                    loadMulti(playlist);
                }
            }

            @Override
            public void noMatches() {
                chat.sendMessage("\u26D4 **Invalid input!**");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                chat.sendMessage("Uh oh! Something went wrong...\nForward this to the dev: ```\n" + exception.getLocalizedMessage() + "```");
            }

            private void loadSingle(AudioTrack track) {
                chat.sendEmbed(String.format(QUEUE_TITLE, MessageUtil.userDiscrimSet(author.getUser()), 1, ""),
                        String.format(QUEUE_DESCRIPTION, CD, getOrNull(track.getInfo().title), "", MIC, getOrNull(track.getInfo().author), ""));
                getTrackManager(guild).queue(track, author);
            }

            private void loadMulti(AudioPlaylist playlist) {
                chat.sendEmbed(String.format(QUEUE_TITLE, MessageUtil.userDiscrimSet(author.getUser()), playlist.getTracks().size(), "s"),
                        String.format(QUEUE_DESCRIPTION, DVD, getOrNull(playlist.getName()), "", "", "", ""));
                for (AudioTrack t : playlist.getTracks()) {
                    getTrackManager(guild).queue(t, author);
                }
            }
        });
    }

    private boolean isDj(Member member) {
        return member.getRoles().stream().anyMatch(r -> r.getName().equals("DJ"));
    }

    private boolean isCurrentDj(Member member) {
        return getTrackManager(member.getGuild()).getTrackInfo(getPlayer(member.getGuild()).getPlayingTrack()).getAuthor().equals(member);
    }

    private boolean isIdle(MessageSender chat, Guild guild) {
        if (!hasPlayer(guild) || getPlayer(guild).getPlayingTrack() == null) {
            chat.sendMessage("No music is being played at the moment!");
            return true;
        }
        return false;
    }

    private void forceSkipTrack(Guild guild, MessageSender chat) {
        getPlayer(guild).stopTrack();
        chat.sendMessage("\u23E9 Skipping track!");
    }

    private void sendHelpMessage(MessageSender chat) {
        chat.sendEmbed("deeJay by dinos#0649", MessageUtil.stripFormatting(Info.PREFIX) + "music\n"
                + "         -> play [url]    - Load a song or a playlist\n"
                + "         -> queue          - View the current queue\n"
                + "         -> skip              - Cast a vote to skip the current track\n"
                + "         -> current        - Display info related to the current track\n"
                + "         -> forceskip**\\***   - Force a skip\n"
                + "         -> shuffle**\\***       - Shuffle the queue\n"
                + "         -> reset**\\***          - Reset the music player\n\n"
                + "Commands with an asterisk**\\*** require the __DJ Role__"
        );
    }

    private String buildQueueMessage(AudioInfo info) {
        AudioTrackInfo trackInfo = info.getTrack().getInfo();
        String title = trackInfo.title;
        long length = trackInfo.length / 1000;
        return getTimestamp(length) + " " + title + "\n";
    }

    private String getTimestamp(long seconds) {
        long hours = Math.floorDiv(seconds, 3600);
        seconds = seconds - (hours * 3600);
        long mins = Math.floorDiv(seconds, 60);
        seconds = seconds - (mins * 60);
        return "`[ " + (hours == 0 ? "" : hours + ":") + String.format("%02d", mins) + ":" + String.format("%02d", seconds) + " ]`";
    }

    private String getOrNull(String s) {
        return s.isEmpty() ? "N/A" : s;
    }
}