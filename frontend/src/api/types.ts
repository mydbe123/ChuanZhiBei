export interface User {
  id: number
  username: string
  displayName: string
  email?: string | null
  role: string
  school?: string | null
  grade?: string | null
  major?: string | null
  learningGoal?: string | null
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
  avatarColor: string
  createdAt: string
}

export interface Conversation {
  id: number
  title: string
  scene: string
  messageCount: number
  updatedAt: string
}

export interface Citation {
  index?: number
  documentTitle: string
  seq: number
  snippet: string
  score: number
}

export interface ToolTraceItem {
  id?: string
  name: string
  arguments?: string
  status?: string
  summary?: string
  artifact?: Record<string, unknown> | null
}

export interface ChatMessage {
  id: number | string
  role: 'user' | 'assistant'
  content: string
  reasoning?: string | null
  toolTrace?: ToolTraceItem[] | null
  citations?: Citation[] | null
  images?: string[]
  createdAt?: string
  streaming?: boolean
  error?: boolean
}

export interface DocumentItem {
  id: number
  title: string
  sourceType: string
  fileName?: string | null
  byteSize: number
  charCount: number
  chunkCount: number
  summary?: string | null
  createdAt: string
  shared: boolean
}

export interface KnowledgeOverview {
  documents: DocumentItem[]
  documentCount: number
  chunkCount: number
  charCount: number
}

export interface QuizSummary {
  id: number
  title: string
  topic: string
  difficulty: string
  questionCount: number
  score: number | null
  status: string
  createdAt: string
}

export interface QuizOption {
  key: string
  text: string
}

export interface QuizQuestion {
  id: number
  type: string
  stem: string
  options: QuizOption[]
  knowledgePoint?: string | null
}

export interface QuizDetail {
  id: number
  title: string
  topic: string
  difficulty: string
  status: string
  score: number | null
  questionCount: number
  createdAt: string
  questions: QuizQuestion[]
}

export interface QuestionResult {
  questionId: number
  stem: string
  options: QuizOption[]
  correctAnswer: string
  userAnswer?: string | null
  correct: boolean
  explanation?: string | null
  knowledgePoint?: string | null
}

export interface SubmitResult {
  quizId: number
  title: string
  score: number
  total: number
  correctCount: number
  results: QuestionResult[]
  weakPoints: string[]
  diagnosis: string
}

export interface StudyTaskView {
  id: number
  day: number
  seq: number
  title: string
  detail?: string | null
  knowledgePoints: string[]
  minutes: number
  status: string
  completedAt?: string | null
}

export interface DayGroup {
  day: number
  totalMinutes: number
  tasks: StudyTaskView[]
}

export interface PlanSummary {
  id: number
  title: string
  goal: string
  level: string
  durationDays: number
  dailyMinutes: number
  summary?: string | null
  totalTasks: number
  doneTasks: number
  progress: number
  createdAt: string
}

export interface PlanDetail {
  plan: PlanSummary
  days: DayGroup[]
}

export interface Stats {
  studyMinutes: number
  activeDays: number
  streakDays: number
  quizzes: number
  avgScore: number
  documents: number
  chunks: number
  aiCalls: number
}

export interface DayActivity {
  date: string
  label: string
  minutes: number
  events: number
}

export interface MasteryPoint {
  point: string
  mastery: number
  attempts: number
}

export interface ActivityItem {
  type: string
  title: string
  detail?: string | null
  at: string
}

export interface AiStats {
  calls: number
  tokens: number
  blocked: number
}

export interface AnalyticsOverview {
  stats: Stats
  weekly: DayActivity[]
  mastery: MasteryPoint[]
  weakPoints: MasteryPoint[]
  recent: ActivityItem[]
  ai: AiStats
  insight: string
}

export interface AiUsageItem {
  id: number
  scene: string
  model: string
  promptTokens: number
  completionTokens: number
  latencyMs: number
  status: string
  blocked: boolean
  detail?: string | null
  createdAt: string
}
