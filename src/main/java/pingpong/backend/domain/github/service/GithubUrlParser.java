package pingpong.backend.domain.github.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pingpong.backend.domain.github.GithubErrorCode;
import pingpong.backend.global.exception.CustomException;

public class GithubUrlParser {

	private static final Pattern GITHUB_PATTERN=
		Pattern.compile("(?:https://github\\.com/|git@github\\.com:|)([^/\\s]+)/([^/\\s.]+)(?:\\.git|)?");

	public record RepoInfo(String owner,String repo){}

	public static RepoInfo parse(String input){
		if (input==null || input.isBlank()){
			throw new CustomException(GithubErrorCode.INVALID_URL_FORMAT);
		}
		Matcher matcher = GITHUB_PATTERN.matcher(input);
		if(matcher.find()){
			return new RepoInfo(matcher.group(1),matcher.group(2));
		}
		throw new CustomException(GithubErrorCode.INVALID_URL_FORMAT);
	}
}
