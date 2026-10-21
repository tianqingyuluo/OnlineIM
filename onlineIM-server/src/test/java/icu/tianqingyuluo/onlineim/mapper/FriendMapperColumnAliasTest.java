package icu.tianqingyuluo.onlineim.mapper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendMapperColumnAliasTest {

    @Test
    void friendAndRequestQueriesUseDistinctIdAliases() throws Exception {
        String friends = read("mapper/FriendResponseMapper.xml");
        assertTrue(friends.contains("uf.id AS friendship_id"));
        assertTrue(friends.contains("u2.id AS user_id"));
        assertTrue(friends.contains("column=\"friendship_id\""));
        assertTrue(friends.contains("column=\"user_id\""));
        assertFalse(friends.contains("column=\"id\""));

        String requests = read("mapper/FriendRequestResponse.xml");
        assertTrue(requests.contains("fr.id AS request_id"));
        assertTrue(requests.contains("u.id AS user_id"));
        assertTrue(requests.contains("column=\"request_id\""));
        assertFalse(requests.contains("column=\"id\""));

        String groups = read("mapper/FriendGroupMapper.xml");
        assertTrue(groups.contains("uf.status = 1"));
        assertTrue(groups.contains("uf.group_id as friendship_group_id"));
        assertFalse(groups.contains("uf.group_id as friend_group_id"));

        String blacklist = read("mapper/UserBriefResponseMapper.xml");
        assertTrue(blacklist.contains("uf.status = 2"));
        assertFalse(blacklist.contains("uf.status = 0"));
    }

    private static String read(String path) throws Exception {
        try (InputStream in = FriendMapperColumnAliasTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
