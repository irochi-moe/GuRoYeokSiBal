package moe.irochi.plugins.guroyeoksibal;

import com.Zrips.CMI.CMI;
import moe.irochi.plugins.guroyeoksibal.hooks.AzuriteChatHook;
import moe.irochi.plugins.guroyeoksibal.hooks.CMIChatHook;
import moe.irochi.plugins.guroyeoksibal.hooks.ChatHook;
import moe.irochi.plugins.guroyeoksibal.hooks.EssentialsChatHook;
import moe.irochi.plugins.guroyeoksibal.hooks.TownyChatHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GuRoYeokSiBal extends JavaPlugin {

    public static final String PERM_BYPASS = "irochi.guroyeoksibal.bypass";
    public static final String PERM_NOTIFY = "irochi.guroyeoksibal.notify";
    public static final String PERM_COOLDOWN_BYPASS = "irochi.guroyeoksibal.cooldown.bypass";
    public static final String PERM_COOLDOWN_TIER_PREFIX = "irochi.guroyeoksibal.cooldown.";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private record PatternSource(String word, String file) {}

    private record WordData(AhoCorasick matcher, int wordCount, Map<String, PatternSource> patterns) {
        private static final WordData EMPTY =
                new WordData(AhoCorasick.build(Set.of()), 0, Map.of());
    }

    private volatile WordData wordData = WordData.EMPTY;
    private volatile List<String> targetCommandPrefixes = List.of();
    private volatile List<String> fullCommandPrefixes = List.of();
    private volatile Map<String, Integer> cooldownTiers = Map.of();
    private volatile TownyChatHook townyChatHook;
    private volatile AzuriteChatHook azuriteChatHook;
    private volatile EssentialsChatHook essentialsChatHook;
    private volatile CMIChatHook cmiChatHook;
    private volatile List<ChatHook> chatHooks = List.of();
    private LanguageManager languageManager;
    private Logger logger;
    private final ConcurrentHashMap<UUID, Map<String, Long>> lastChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> pendingCooldown = new ConcurrentHashMap<>();
    private final AtomicLong lastCooldownSweep = new AtomicLong(System.nanoTime());

    @Override
    public void onEnable() {
        logger = getLogger();
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        loadAll(false);

        getServer().getPluginManager().registerEvents(new ChatFilterListener(this), this);

        AdminCommand handler = new AdminCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("guroyeoksibal"));
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        if (getConfig().getBoolean("bstats", true)) {
            new Metrics(this, 33411);
        }
    }

    public boolean isAzuriteHooked() {
        return azuriteChatHook != null;
    }

    public boolean isCMIHooked() {
        return cmiChatHook != null;
    }

    // Azurite와 CMI는 이벤트 취소가 정상 전달 경로(취소 후 자체 발송)
    public boolean resendsCancelledChat() {
        return azuriteChatHook != null || cmiChatHook != null;
    }

    public void clearTownyDirectedChat(Player player) {
        TownyChatHook hook = townyChatHook;
        if (hook == null) return;
        // TownyChat의 directedChat은 스레드 안전하지 않은 WeakHashMap이라 메인 스레드에서만 수정
        getServer().getGlobalRegionScheduler().run(this, task -> hook.clearDirectedChat(player));
    }

    public record ChatDecision(boolean filter, boolean cancellable, String cooldownKey) {}

    public ChatDecision evaluateChat(Player player, String message) {
        List<ChatHook> hooks = chatHooks;
        boolean filter = true;
        boolean cancellable = true;
        boolean cooldownApplies = true;
        StringBuilder key = new StringBuilder();
        for (ChatHook hook : hooks) {
            ChatHook.Decision decision = hook.evaluate(player, message);
            filter = filter && decision.filter();
            cancellable = cancellable && decision.cancellable();
            cooldownApplies = cooldownApplies && decision.cooldownApplies();
            if (!key.isEmpty()) key.append('|');
            key.append(decision.cooldownKey());
        }
        String cooldownKey = !cooldownApplies ? null : hooks.isEmpty() ? "default" : key.toString();
        return new ChatDecision(filter, cancellable, cooldownKey);
    }

    // 외부 플러그인용 공개 API (시그니처 유지)
    public boolean shouldFilter(Player player, String message) {
        return evaluateChat(player, message).filter();
    }

    public long checkCooldown(Player player, String key) {
        UUID uuid = player.getUniqueId();
        pendingCooldown.remove(uuid);

        if (key == null) return 0;
        if (!isChatCooldownEnabled()) return 0;
        int seconds = getEffectiveCooldownSeconds(player);
        if (seconds <= 0) return 0;

        long now = System.nanoTime();
        sweepCooldowns(now);
        long cooldownNanos = seconds * 1_000_000_000L;
        Map<String, Long> channels = lastChat.get(uuid);
        Long lastTime = channels != null ? channels.get(key) : null;
        if (lastTime != null && now - lastTime < cooldownNanos) {
            return (cooldownNanos - (now - lastTime) + 999_999_999L) / 1_000_000_000L;
        }

        pendingCooldown.put(uuid, key);
        return 0;
    }

    public void abortCooldown(Player player) {
        pendingCooldown.remove(player.getUniqueId());
    }

    public void commitCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        String key = pendingCooldown.remove(uuid);
        if (key == null) return;
        lastChat.compute(uuid, (k, channels) -> {
            if (channels == null) channels = new ConcurrentHashMap<>();
            channels.put(key, System.nanoTime());
            return channels;
        });
    }

    private void sweepCooldowns(long now) {
        long last = lastCooldownSweep.get();
        if (now - last < 60L * 1_000_000_000L) return;
        if (!lastCooldownSweep.compareAndSet(last, now)) return;
        long ttlNanos = Math.max(60, maxConfiguredCooldownSeconds()) * 1_000_000_000L;
        for (UUID id : lastChat.keySet()) {
            lastChat.computeIfPresent(id, (k, channels) -> {
                channels.values().removeIf(t -> now - t > ttlNanos);
                return channels.isEmpty() ? null : channels;
            });
        }
    }

    private String describeChannelList(List<String> channels) {
        if (channels.stream().anyMatch(c -> c.trim().equals("*"))) return "전체";
        if (channels.isEmpty()) return "없음";
        return String.join(", ", channels);
    }

    private String hookStatus(boolean isReload, String filterKey, String cooldownKey) {
        return (isReload ? "갱신: " : "감지: ")
                + "필터: " + describeChannelList(getConfig().getStringList(filterKey))
                + " | 쿨타임: " + describeChannelList(getConfig().getStringList(cooldownKey));
    }

    private Set<String> getFilteredSet(String configKey) {
        return getConfig().getStringList(configKey).stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private void initTownyChatHook(boolean isReload) {
        if (!getServer().getPluginManager().isPluginEnabled("TownyChat")) {
            townyChatHook = null;
            return;
        }

        townyChatHook = new TownyChatHook(
                getFilteredSet("towny-filtered-channels"),
                getFilteredSet("towny-cooldown-channels"),
                logger);
        logger.info("TownyChat " + hookStatus(isReload, "towny-filtered-channels", "towny-cooldown-channels"));
    }

    private void initAzuriteChatHook(boolean isReload) {
        Plugin azurite = getServer().getPluginManager().getPlugin("Azurite");
        if (azurite == null || !azurite.isEnabled()) {
            azuriteChatHook = null;
            return;
        }

        try {
            azuriteChatHook = new AzuriteChatHook(
                    azurite,
                    getFilteredSet("azurite-filtered-chats"),
                    getFilteredSet("azurite-cooldown-chats"),
                    logger);
            logger.info("Azurite " + hookStatus(isReload, "azurite-filtered-chats", "azurite-cooldown-chats"));
            for (String key : List.of("azurite-filtered-chats", "azurite-cooldown-chats")) {
                if (!getFilteredSet(key).stream().allMatch("public"::equals)) {
                    logger.warning(key + ": public 외 채팅 종류는 차단(BLOCK/쿨타임)이 적용되지 않습니다. REPLACE 검열만 동작합니다.");
                }
            }
        } catch (RuntimeException e) {
            azuriteChatHook = null;
            logger.warning("Azurite 연동 초기화 실패: " + e.getMessage());
        }
    }

    private void initEssentialsChatHook(boolean isReload) {
        Plugin essentials = getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null || !getServer().getPluginManager().isPluginEnabled("EssentialsChat")) {
            essentialsChatHook = null;
            return;
        }

        essentialsChatHook = new EssentialsChatHook(
                essentials,
                getFilteredSet("essentials-filtered-chats"),
                getFilteredSet("essentials-cooldown-chats"));
        logger.info("EssentialsX Chat " + hookStatus(isReload, "essentials-filtered-chats", "essentials-cooldown-chats")
                + " | chat.radius: " + essentialsChatHook.getRadius());
    }

    private void initCMIChatHook(boolean isReload) {
        Plugin cmi = getServer().getPluginManager().getPlugin("CMI");
        if (cmi == null || !cmi.isEnabled()) {
            cmiChatHook = null;
            return;
        }

        if (!isCMIChatFormatEnabled()) {
            cmiChatHook = null;
            logger.info("CMI 감지: ModifyChatFormat이 꺼져 있어 연동하지 않습니다.");
            return;
        }

        cmiChatHook = new CMIChatHook(
                getFilteredSet("cmi-filtered-chats"),
                getFilteredSet("cmi-cooldown-chats"),
                logger);
        logger.info("CMI " + hookStatus(isReload, "cmi-filtered-chats", "cmi-cooldown-chats"));
    }

    private static boolean isCMIChatFormatEnabled() {
        try {
            return CMI.getInstance().getChatManager().isModifyChatFormat();
        } catch (RuntimeException | LinkageError e) {
            return true;
        }
    }

    private void loadBannedWords(boolean isReload) {
        File dataFolder = getDataFolder();
        dataFolder.mkdirs();

        File defaultCsv = new File(dataFolder, "default.csv");
        if (!defaultCsv.exists()) {
            saveResource("default.csv", false);
        }

        File[] csvFiles = dataFolder.listFiles((dir, name) ->
                !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".csv"));

        if (csvFiles == null || csvFiles.length == 0) {
            logger.warning("CSV 파일을 찾을 수 없습니다: " + dataFolder.getPath());
            wordData = WordData.EMPTY;
            return;
        }

        Arrays.sort(csvFiles, Comparator.comparing(File::getName));

        Set<String> words = new HashSet<>();
        Map<String, PatternSource> patterns = new HashMap<>();
        boolean anyFailure = false;

        for (File csvFile : csvFiles) {
            int fileCount = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.replace("\uFEFF", "").trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    for (String token : line.split(",")) {
                        String word = MessageFilter.normalize(token.trim());
                        if (word.isEmpty()) continue;
                        if (words.add(word)) {
                            fileCount++;
                        }
                        for (String pattern : MessageFilter.patternVariants(word)) {
                            patterns.putIfAbsent(pattern, new PatternSource(word, csvFile.getName()));
                        }
                    }
                }
                logger.info(csvFile.getName() + "에서 " + fileCount + "개 단어 로드");
            } catch (IOException e) {
                anyFailure = true;
                logger.severe(csvFile.getName() + " 로드 실패: " + e.getMessage());
            }
        }

        if (isReload && anyFailure) {
            logger.severe("CSV 로드에 실패하여 기존 금칙어 목록(" + wordData.wordCount() + "개)을 유지합니다.");
            return;
        }

        wordData = new WordData(AhoCorasick.build(patterns.keySet()), words.size(), Map.copyOf(patterns));
        logger.info((isReload ? "리로드 완료" : "준비 완료") + ", 단어 " + words.size() + "개");
    }

    public void reloadAll() {
        reloadConfig();
        loadAll(true);
    }

    private void loadAll(boolean isReload) {
        // validateConfig()가 cooldownTiers를 읽으므로 loadCooldownTiers()가 먼저
        loadCooldownTiers();
        validateConfig();
        loadCommandPrefixes();
        languageManager.load(getConfig().getString("fallback-language", "en"));
        loadBannedWords(isReload);
        initTownyChatHook(isReload);
        initAzuriteChatHook(isReload);
        initEssentialsChatHook(isReload);
        initCMIChatHook(isReload);
        chatHooks = Stream.<ChatHook>of(townyChatHook, azuriteChatHook, essentialsChatHook, cmiChatHook)
                .filter(Objects::nonNull)
                .toList();
    }

    public AhoCorasick getActiveMatcher() {
        WordData data = wordData;
        return data.wordCount() == 0 ? null : data.matcher();
    }

    public int getLoadedWordCount() {
        return wordData.wordCount();
    }

    public String describePattern(String pattern) {
        PatternSource src = wordData.patterns().get(pattern);
        return src != null ? "\"" + src.word() + "\" (" + src.file() + ")" : "\"" + pattern + "\" (unknown)";
    }

    public String getActionMode() {
        return "REPLACE".equalsIgnoreCase(getConfig().getString("action", "BLOCK").trim()) ? "REPLACE" : "BLOCK";
    }

    private void validateConfig() {
        String action = getConfig().getString("action", "BLOCK").trim();
        if (!action.equalsIgnoreCase("BLOCK") && !action.equalsIgnoreCase("REPLACE")) {
            logger.warning("action 값이 올바르지 않습니다: '" + action + "', BLOCK으로 동작합니다. (BLOCK 또는 REPLACE)");
        }
        String replaceChar = getConfig().getString("replace-char", "*");
        if (replaceChar.length() != 1 || replaceChar.charAt(0) != getReplaceChar()) {
            logger.warning("replace-char는 1글자만 사용할 수 있습니다: '" + replaceChar + "', '" + getReplaceChar() + "'로 동작합니다.");
        }
        int defaultSeconds = configuredCooldownSeconds();
        for (Map.Entry<String, Integer> tier : cooldownTiers.entrySet()) {
            if (defaultSeconds > 0 && tier.getValue() > defaultSeconds) {
                logger.warning("chat-cooldown-tiers." + tier.getKey() + "(" + tier.getValue()
                        + ")가 chat-cooldown-seconds(" + defaultSeconds + ")보다 깁니다. 설정을 확인하세요.");
            }
        }
    }

    public char getReplaceChar() {
        String s = getConfig().getString("replace-char", "*");
        if (s.isEmpty()) return '*';
        char c = s.charAt(0);
        if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
            return '*';
        }
        return c;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public Component getBlockMessage(Player player) {
        return languageManager.get(player, "block-message");
    }

    public Component getCapsBlockMessage(Player player) {
        return languageManager.get(player, "caps-block-message");
    }

    public boolean isBlockAllCaps() {
        return getConfig().getBoolean("block-all-caps", false);
    }

    public int getBlockAllCapsMinLetters() {
        return Math.max(1, getConfig().getInt("block-all-caps-min-letters", 4));
    }

    private boolean isChatCooldownEnabled() {
        return getConfig().getBoolean("chat-cooldown-enabled", true);
    }

    private int getEffectiveCooldownSeconds(Player player) {
        if (player.hasPermission(PERM_COOLDOWN_BYPASS)) return 0;
        Integer best = null;
        for (Map.Entry<String, Integer> tier : cooldownTiers.entrySet()) {
            if (!player.hasPermission(PERM_COOLDOWN_TIER_PREFIX + tier.getKey())) continue;
            if (best == null || tier.getValue() < best) {
                best = tier.getValue();
            }
        }
        return Math.max(0, best != null ? best : configuredCooldownSeconds());
    }

    private int configuredCooldownSeconds() {
        return getConfig().getInt("chat-cooldown-seconds", 15);
    }

    private int maxConfiguredCooldownSeconds() {
        int max = configuredCooldownSeconds();
        for (int seconds : cooldownTiers.values()) {
            max = Math.max(max, seconds);
        }
        return max;
    }

    private void loadCooldownTiers() {
        Map<String, Integer> tiers = new LinkedHashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("chat-cooldown-tiers");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                String tier = name.trim().toLowerCase(Locale.ROOT);
                if (tier.isEmpty()) continue;
                if (tier.equals("bypass")) {
                    logger.warning("chat-cooldown-tiers의 'bypass'는 쿨타임 우회 권한과 겹치므로 무시합니다.");
                    continue;
                }
                tiers.put(tier, section.getInt(name, 0));
            }
        }
        cooldownTiers = Map.copyOf(tiers);
    }

    public Component getCooldownMessage(Player player, long remainingSeconds) {
        return languageManager.get(player, "cooldown-message",
                Map.of("seconds", String.valueOf(remainingSeconds)));
    }

    public boolean isFilterCommands() {
        return getConfig().getBoolean("filter-commands", true);
    }

    public List<String> getTargetCommandPrefixes() {
        return targetCommandPrefixes;
    }

    public List<String> getFullCommandPrefixes() {
        return fullCommandPrefixes;
    }

    private void loadCommandPrefixes() {
        targetCommandPrefixes = normalizePrefixes(getConfig().getStringList("commands-with-target"));
        fullCommandPrefixes = normalizePrefixes(getConfig().getStringList("commands-full"));
    }

    private static List<String> normalizePrefixes(List<String> commands) {
        return commands.stream()
                .map(cmd -> "/" + cmd.trim().toLowerCase(Locale.ROOT) + " ")
                .toList();
    }

    public boolean isNotifyStaff() {
        return getConfig().getBoolean("notify-staff", true);
    }

    public Component getNotifyMessage(Player viewer, String playerName, String message) {
        return languageManager.get(viewer, "notify-format", Map.of(
                "player", MM.escapeTags(playerName),
                "message", MM.escapeTags(MessageFilter.stripLegacyCodes(message))));
    }
}
