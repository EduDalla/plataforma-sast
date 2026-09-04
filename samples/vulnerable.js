const password = "123456";

function execute(userInput) {
  eval(userInput);
  document.querySelector("#output").innerHTML = userInput;
}
