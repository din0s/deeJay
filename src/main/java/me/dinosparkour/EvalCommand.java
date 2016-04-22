package me.dinosparkour;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.Permission;
import net.dv8tion.jda.entities.Guild;
import net.dv8tion.jda.entities.Message;
import net.dv8tion.jda.entities.TextChannel;
import net.dv8tion.jda.entities.User;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.exceptions.VerificationLevelException;
import net.dv8tion.jda.hooks.ListenerAdapter;
import net.dv8tion.jda.player.Bot;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.concurrent.*;

public class EvalCommand extends ListenerAdapter {

    private final ScriptEngine engine;

    public EvalCommand() {
        engine = new ScriptEngineManager().getEngineByName("nashorn");
        try {
            engine.eval("var imports = new JavaImporter(java.io, java.lang, java.util);");

        } catch (ScriptException ex) {
            ex.printStackTrace();
        }
    }

    private static void sendMessage(String msg, GuildMessageReceivedEvent e) {
        User author = e.getAuthor();
        TextChannel channel = e.getChannel();

        if (!channel.checkPermission(e.getJDA().getSelfInfo(), Permission.MESSAGE_WRITE))
            author.getPrivateChannel().sendMessage("Missing `MESSAGE_WRITE` permission!");
        else {
            if (msg.contains("@everyone"))
                msg = msg.replace("@everyone", "@\u180Eeveryone");
            if (msg.contains("@here"))
                msg = msg.replace("@here", "@\u180Ehere");

            try {
                channel.sendMessage(msg);

            } catch (VerificationLevelException ex) {
                author.getPrivateChannel().sendMessage("Missing Verification requirement!");
            }
        }
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {

        JDA jda = e.getJDA();
        TextChannel channel = e.getChannel();
        User author = e.getAuthor();
        Message message = e.getMessage();
        String msg = message.getContent();
        Guild guild = e.getGuild();

        String prefix = Bot.prefix;

        //Specifically listen for the eval command
        if (!author.getId().equals(Bot.AUTHOR_ID)
                || !msg.startsWith(prefix + "eval")
                || !msg.contains(" ")) return;

        String input = msg.substring(msg.indexOf(' ')+1);

        engine.put("e", e);
        engine.put("jda", jda);
        engine.put("channel", channel);
        engine.put("author", author);
        engine.put("message", message);
        engine.put("guild", guild);
        engine.put("input", input);

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> future = service.schedule(() -> {

            Object out = null;
            try {
                out = engine.eval(
                        "(function() {" +
                                "with (imports) {\n" + input + "\n}" +
                                "})();");

            } catch (Exception ex) {
                sendMessage("**Exception**: ```\n" + ex.getLocalizedMessage() + "```", e);
                return;
            }

            String outputS;
            if(out == null)
                outputS = "`Task executed without errors.`";
            else if(out.toString().length() >= 1985 )
                outputS = "The output is longer than 2000 chars!";
            else if(out.toString().contains("\n"))
                outputS = "Output: ```\n" + out.toString().replace("`", "\\`") + "\n```";
            else
                outputS = "Output: ` " + out.toString().replace("`", "") + " `";

            sendMessage(outputS, e);

        }, 0, TimeUnit.MILLISECONDS);

        Thread script = new Thread("eval code") {

            @Override
            public void run() {
                try {
                    future.get(10, TimeUnit.SECONDS);

                } catch (TimeoutException ex) {
                    future.cancel(true);
                    sendMessage("Your task exceeds the time limit!", e);

                } catch (ExecutionException | InterruptedException  ex) {
                    ex.printStackTrace();
                }
            }
        };
        script.start();
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent e) {

        JDA jda = e.getJDA();
        TextChannel channel = e.getChannel();
        User author = e.getAuthor();
        Message message = e.getMessage();
        String msg = message.getContent();
        Guild guild = e.getGuild();

        String prefix = Bot.prefix;

        //Specifically listen for the eval command
        if (!author.getId().equals(Bot.AUTHOR_ID)
                || !msg.startsWith(prefix + "eval")
                || !msg.contains(" ")) return;

        String input = msg.substring(msg.indexOf(' ') + 1);

        engine.put("e", e);
        engine.put("jda", jda);
        engine.put("channel", channel);
        engine.put("author", author);
        engine.put("message", message);
        engine.put("guild", guild);
        engine.put("input", input);

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> future = service.schedule(() -> {

            Object out = null;
            try {
                out = engine.eval(
                        "(function() {" +
                                "with (imports) {\n" + input + "\n}" +
                                "})();");

            } catch (Exception ex) {
                sendMessage("**Exception**: ```\n" + ex.getLocalizedMessage() + "```", e);
                return;
            }

            String outputS;
            if (out == null)
                outputS = "`Task executed without errors.`";
            else if (out.toString().length() >= 1985)
                outputS = "The output is longer than 2000 chars!";
            else if (out.toString().contains("\n"))
                outputS = "Output: ```\n" + out.toString().replace("`", "\\`") + "\n```";
            else
                outputS = "Output: ` " + out.toString().replace("`", "") + " `";

            sendMessage(outputS, e);

        }, 0, TimeUnit.MILLISECONDS);

        Thread script = new Thread("eval code") {

            @Override
            public void run() {
                try {
                    future.get(10, TimeUnit.SECONDS);

                } catch (TimeoutException ex) {
                    future.cancel(true);
                    sendMessage("Your task exceeds the time limit!", e);

                } catch (ExecutionException | InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        };
        script.start();
    }
}