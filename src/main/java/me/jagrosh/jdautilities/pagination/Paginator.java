/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.jagrosh.jdautilities.pagination;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import me.jagrosh.jdautilities.waiter.EventWaiter;
import menu.Menu;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.requests.RestAction;

/**
 *
 * @author John Grosh
 */
public class Paginator extends Menu {
    
    private final BiFunction<Integer,Integer,Color> color;
    private final BiFunction<Integer,Integer,String> text;
    private final int columns;
    private final int itemsPerPage;
    private final boolean showPageNumbers;
    private final boolean numberItems;
    private final List<String> strings;
    private final int pages;
    
    public static final String LEFT = "\u25C0";
    public static final String STOP = "\u23F9";
    public static final String RIGHT = "\u25B6";
    
    protected Paginator(EventWaiter waiter, Set<User> users, Set<Role> roles, long timeout, TimeUnit unit,
            BiFunction<Integer,Integer,Color> color, BiFunction<Integer,Integer,String> text,
            int columns, int itemsPerPage, boolean showPageNumbers, boolean numberItems, List<String> items)
    {
        super(waiter, users, roles, timeout, unit);
        this.color = color;
        this.text = text;
        this.columns = columns;
        this.itemsPerPage = itemsPerPage;
        this.showPageNumbers = showPageNumbers;
        this.numberItems = numberItems;
        this.strings = items;
        this.pages = (int)Math.ceil((double)strings.size()/itemsPerPage);
    }
    
    /**
     * Begins pagination, sending a new message to the provided channel
     * @param channel the channel in which to begin pagination
     * @param pageNum the starting page
     */
    public void paginate(MessageChannel channel, int pageNum)
    {
        if(pageNum<1)
            pageNum = 1;
        else if (pageNum>pages)
            pageNum = pages;
        Message msg = renderPage(pageNum);
        initialize(channel.sendMessage(msg), pageNum);
    }
    
    /**
     * Begins pagination, editing the menu into the provided message
     * @param message the message to use for pagination
     * @param pageNum the starting page
     */
    public void paginate(Message message, int pageNum)
    {
        if(pageNum<1)
            pageNum = 1;
        else if (pageNum>pages)
            pageNum = pages;
        Message msg = renderPage(pageNum);
        initialize(message.editMessage(msg), pageNum);
    }
    
    private void initialize(RestAction<Message> action, int pageNum)
    {
        action.queue(m->{
            if(pages>1)
            {
                m.addReaction(LEFT).queue();
                m.addReaction(STOP).queue();
                m.addReaction(RIGHT).queue(v -> pagination(m, pageNum), t -> pagination(m, pageNum));
            }
            else
            {
                m.addReaction(STOP).queue(v -> pagination(m, pageNum), t -> pagination(m, pageNum));
            }
        });
    }
    
    private void pagination(Message message, int pageNum)
    {
        waiter.waitForEvent(MessageReactionAddEvent.class, (MessageReactionAddEvent event) -> {
            if(!event.getMessageId().equals(message.getId()))
                return false;
            if(!(LEFT.equals(event.getReaction().getEmote().getName()) 
                    || STOP.equals(event.getReaction().getEmote().getName())
                    || RIGHT.equals(event.getReaction().getEmote().getName())))
                return false;
            if(users.isEmpty() && roles.isEmpty())
                return true;
            if(users.contains(event.getUser()))
                return true;
            if(!(event.getChannel() instanceof TextChannel))
                return false;
            Member m = ((TextChannel)event.getChannel()).getGuild().getMember(event.getUser());
            return m.getRoles().stream().anyMatch(r -> roles.contains(r));
        }, event -> {
            int newPageNum = pageNum;
            switch(event.getReaction().getEmote().getName())
            {
                case LEFT:  if(newPageNum>1) newPageNum--; break;
                case RIGHT: if(newPageNum<pages) newPageNum++; break;
                case STOP: message.deleteMessage().queue(); return;
            }
            event.getReaction().removeReaction(event.getUser()).queue();
            int n = newPageNum;
            message.editMessage(renderPage(newPageNum)).queue(m -> {
                pagination(m, n);
            });
        }, timeout, unit, () -> message.deleteMessage().queue());
    }
    
    private Message renderPage(int pageNum)
    {
        StringBuilder strbuilder = new StringBuilder();
        MessageBuilder mbuilder = new MessageBuilder();
        EmbedBuilder ebuilder = new EmbedBuilder();
        int start = (pageNum-1)*itemsPerPage;
        int end = strings.size() < pageNum*itemsPerPage ? strings.size() : pageNum*itemsPerPage;
        switch(columns)
        {
            
            case 1:
                for(int i=start; i<end; i++)
                    strbuilder.append("\n").append(numberItems ? (i+1)+". " : "").append(strings.get(i));
                ebuilder.setDescription(strbuilder.toString());
                break;
            default:
                int per = (int)Math.ceil((double)(end-start)/columns);
                for(int k=0; k<columns; k++)
                {
                    for(int i=start+k*per; i<end && i<start+(k+1)*per; i++)
                        strbuilder.append("\n").append(numberItems ? (i+1)+". " : "").append(strings.get(i));
                    ebuilder.addField("", strbuilder.toString(), true);
                }
        }
        
        ebuilder.setColor(color.apply(pageNum, pages));
        if(showPageNumbers)
            ebuilder.setFooter("Page "+pageNum+"/"+pages, null);
        mbuilder.setEmbed(ebuilder.build());
        if(text!=null)
            mbuilder.append(text.apply(pageNum, pages));
        return mbuilder.build();
    }
}
