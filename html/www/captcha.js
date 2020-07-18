window.captcha_key = '6Le_F8gUAAAAAIKOU-7X6z78cBt6I4OrCa8J-ojZ';

function grecaptcha_renew(action){
	if(!action) action = 'none';
	if(grecaptcha){
		grecaptcha.execute(window.captcha_key, {action: action}).then(function(token) {
			window.captcha_token = token;
		});
	}
}