package io.github.lightman314.lightmanscurrency.client.gui.team;

import java.util.List;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.client.gui.screen.TeamManagerScreen;
import io.github.lightman314.lightmanscurrency.client.gui.widget.ScrollTextDisplay;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public class TeamMemberListTab extends TeamTab {

	public static final TeamMemberListTab INSTANCE = new TeamMemberListTab();
	
	private TeamMemberListTab() { }
	
	@Override
	public IconData getIcon() {
		return IconData.of(Items.PLAYER_HEAD);
	}

	@Override
	public Component getTooltip() {
		return new TranslatableComponent("tooltip.lightmanscurrency.team.members");
	}

	@Override
	public boolean allowViewing(Player player, Team team) {
		return team != null;
	}
	
	ScrollTextDisplay memberDisplay;

	@Override
	public void initTab() {
		TeamManagerScreen screen = this.getScreen();
		
		this.memberDisplay = screen.addRenderableTabWidget(new ScrollTextDisplay(screen.guiLeft() + 10, screen.guiTop() + 10, screen.xSize - 20, screen.ySize - 20, this.getFont(), this::getMemberList));
		this.memberDisplay.setColumnCount(2);
		
	}
	
	private List<Component> getMemberList()
	{
		List<Component> list = Lists.newArrayList();
		Team team = this.getActiveTeam();
		if(team != null)
		{
			//List Owner
			list.add(new TextComponent(team.getOwner().lastKnownName()).withStyle(ChatFormatting.GREEN));
			//List Admins
			team.getAdmins().forEach(admin -> list.add(new TextComponent(admin.lastKnownName()).withStyle(ChatFormatting.DARK_GREEN)));
			//List members
			team.getMembers().forEach(member -> list.add(new TextComponent(member.lastKnownName())));
		}
		
		return list;
	}

	@Override
	public void preRender(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		
	}

	@Override
	public void postRender(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
		
	}

	@Override
	public void tick() {
		
	}

	@Override
	public void closeTab() {
		
	}

}