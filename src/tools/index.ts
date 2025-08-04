import {
  definition as getWeatherForecastDefinition,
  fn as getWeatherForecastFunction,
} from './getWeatherForecast'
import { definition as getTodaysDateDefinition, fn as getTodaysDateFunction } from './getTodaysDate'
import { definition as calculatorDefinition, fn as calculatorFunction } from './calculator'
import { definition as searxngDefinition, fn as searxngFunction } from './searxng'

export const toolDefinitions = [
  getWeatherForecastDefinition,
  getTodaysDateDefinition,
  calculatorDefinition,
  searxngDefinition,
]

export const toolFunctions: { [name: string]: Function } = {
  get_weather_forecast: getWeatherForecastFunction,
  get_todays_date: getTodaysDateFunction,
  simple_calculator: calculatorFunction,
  web_search: searxngFunction,
}
